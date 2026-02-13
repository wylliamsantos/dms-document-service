## dms-document-service

Serviço responsável pela ingestão, atualização e consulta de documentos no DMS. Persistimos metadados em MongoDB, binários em S3 compatível e cacheamos informações derivadas em Redis.

### Build & Test

- `./gradlew compileJava` — valida o projeto com Java 21.
- `./gradlew test` — executa a suíte de testes.
- `./gradlew bootRun` — sobe a aplicação localmente (Mongo/Redis/S3 devem estar acessíveis).
- `./scripts/package-a-smoke.sh` — smoke test do Pacote A (token/401/403/400/CORS) com Keycloak local.

### Endpoints principais (todos sob `/v1/documents`)

- `POST /` (multipart ou `/base64`) — cria documentos; controla versão automática (major/minor) e assinatura digital quando configurada.
- `GET /{documentId}/information` — retorna metadados da última versão (ou `/ {documentId}/{version}/information`).
- `GET /{documentId}/versions` — lista histórico de versões.
- `GET /{documentId}/content` e `/base64` — recupera conteúdo (binário ou Base64).
- `POST /{documentId}` — cria nova versão via multipart (major/minor).
- `DELETE /{documentId}` — remove documento e limpa caches.
- `POST /presigned/url` e `PUT /{documentId}/finalize` — fluxo assíncrono para uploads grandes/vídeos (detalhado abaixo).

#### Fluxo de upload assíncrono (presigned URL)

1. **Solicitar URL pré-assinada** — `POST /v1/documents/presigned/url`

   ```jsonc
   {
     "category": "ac:procuracao",          // obrigatório, validação de categoria
     "metadata": "{\"cpf\":\"12345678900\"}",
     "isFinal": true,                        // controla versão major/minor
     "fileName": "contrato-assinado.pdf",   // nome lógico final
     "fileSize": 10485760,                   // tamanho em bytes
     "mimeType": "application/pdf",
     "issuingDate": "2024-01-31",           // opcional
     "author": "João Silva",                // obrigatório
     "comment": "Reenvio com assinatura"
   }
   ```

   Resposta: `{ "id": { "id": "<documentId>", "version": "<version>" }, "url": "https://s3..." }`

2. **Upload direto para S3 compatível** — executar `PUT` na `url` retornada utilizando o `Content-Type` informado. O frontend acompanha o progresso e bloqueia a página até concluir.

3. **Finalizar o upload** — `PUT /v1/documents/{documentId}/finalize`

   ```json
   {
     "version": "1.0",               // versão retornada no passo 1
     "fileSize": 10485760,
     "mimeType": "application/pdf"
   }
   ```

   A API valida integridade, atualiza metadados e devolve `{ "id": "…", "version": "…" }` para consumo pela interface.

### Detalhes de implementação relevantes

- Versões (`DmsDocumentVersion`) passam a salvar `modifiedAt` na criação inicial, alinhando histórico para o frontend.
- CORS configurável via `dms.cors.*` em `application.yml` / variáveis `DMS_CORS_ALLOWED_ORIGINS` etc.
- Enum `VersionType` agora compartilhado em `br.com.dms.domain.core`, reutilizado por ambos os serviços.
- Workflow de revisão exposto via `/v1/workflow/**` (histórico, fila de pendências e review action).

### Chave de negócio dinâmica (Business Key)

O serviço não fica mais acoplado apenas a CPF para upsert de documentos.

- `DmsDocument` agora mantém:
  - `businessKeyType` (ex.: `cpf`, `placa`, `renavam`)
  - `businessKeyValue` (valor efetivo da chave)
- A resolução da chave considera `uniqueAttributes` da categoria (primeiro atributo da lista). Se não houver configuração, fallback para `cpf`.
- Upsert (multipart/base64/presigned) usa `businessKeyType + businessKeyValue + filename + category` para localizar o documento.
- Compatibilidade: quando a chave efetiva for `cpf`, o campo legado `cpf` continua sendo preenchido.

Com isso, o DMS fica pronto para casos de uso além de pessoa física (ex.: DETRAN com chave por placa).

### Execução local

1. Exportar Java 21 (GraalVM local):
   ```bash
   export JAVA_HOME=/Users/wylliamsantos/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.6/Contents/Home
   export PATH="$JAVA_HOME/bin:$PATH"
   ```
2. Garantir MongoDB, Redis e S3 (MinIO) disponíveis conforme `application-local.yml`.
3. (Opcional) Subir o Keycloak local (ver seção abaixo) para testar autenticação.
4. `./gradlew bootRun`.

### Smoke do Pacote A (auth + validação + CORS)

Com `dms-document-service` e Keycloak locais em execução, rode:

```bash
./scripts/package-a-smoke.sh
```

Checks executados:
- health do serviço e discovery do realm;
- emissão de token para usuário viewer e admin;
- `401` sem token;
- `403` para viewer em endpoint restrito de admin;
- `400` para payload inválido com token admin;
- preflight CORS (`OPTIONS`) em endpoint `/v1/**`.

Variáveis úteis (opcionais): `DOC_API_URL`, `KEYCLOAK_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_VIEWER_USER`, `KEYCLOAK_VIEWER_PASS`, `KEYCLOAK_ADMIN_USER`, `KEYCLOAK_ADMIN_PASS`, `CORS_ORIGIN`.

### Autenticação via Keycloak (ambiente local)

O `docker-compose.yml` agora inclui um serviço `dms-keycloak` com um realm de exemplo (`dms`).

1. Inicie a stack local:
   ```bash
   docker-compose up -d keycloak
   ```
   Usuários disponíveis:
   - `admin / admin123` (roles `ROLE_ADMIN`, `ROLE_DOCUMENT_VIEWER`)
   - `viewer / viewer123` (role `ROLE_DOCUMENT_VIEWER`)

2. O serviço expõe o console e endpoints em `http://localhost:8180/`. As configurações principais estão no arquivo `docker/keycloak/realm-export.json` (clientes `dms-frontend` e `dms-api`).

3. O `dms-document-service` foi configurado como resource-server OAuth2 (`spring.security.oauth2.resourceserver.jwt.issuer-uri`). Ao receber um token JWT do realm `dms`, ele valida a assinatura e usa os roles (`realm_access`/`resource_access`) como `GrantedAuthority`.

4. Próximos passos sugeridos:
   - Ajustar o frontend para autenticar via Keycloak (PKCE) em vez da tela de login simulada.
   - Criar uma configuração equivalente no `dms-search-service` para padronizar a segurança entre os serviços.

### Observabilidade

- Actuator: `/actuator/health`, `/actuator/prometheus` habilitados.
- Ehcache 2.x para cache de categorias/tipos/localizações.

## dms-search-service

Serviço de consulta que expõe as pesquisas para o DMS, agora inteiramente alimentado por MongoDB.

### Build & Test

- `./gradlew compileJava`
- `./gradlew test`
- `./gradlew bootRun`

### Endpoint ativo

- `POST /v1/search/byCpf`
  ```json
  {
    "cpf": "12345678900",
    "documentCategoryNames": ["ac:identificacao"],
    "searchScope": "ALL" | "VALID" | "EXPIRED" | "LATEST",
    "versionType": "MAJOR" | "MINOR" | "ALL"
  }
  ```
  Retorna um `Page<EntryPagination>` com a versão mais apropriada por documento (major por padrão). Suporta filtros por escopo de expiração (metadado `dataExpiracao`) e traz informações de conteúdo/versão.

  • As antigas buscas (`/byAuthor`, `/byMetadata`, `/byQuery`) foram removidas — consumidores devem migrar para `/byCpf` ou aguardar novos endpoints Mongo-based.

### Integração com Mongo

- Reutiliza os documentos `DmsDocument` e `DmsDocumentVersion` com índices por `cpf` e `category`.
- Repositórios Spring Data: `DmsDocumentRepository`, `DmsDocumentVersionRepository`.
- O serviço resolve categorias válidas (`DocumentCategoryRepository`) e monta a resposta para UI (tabela, versões).

### CORS

- Mesmo padrão do document-service (`dms.cors.allowed-origins` etc.).

### Execução local

1. Ajustar `.env.local` no frontend para apontar para `http://localhost:8081`.
2. Subir Mongo com os metadados do DMS (`dms` database esperado).
3. `./gradlew bootRun` (exportar `JAVA_HOME` igual ao document-service).

### Observabilidade

- Actuator `/actuator/health`, `/actuator/prometheus` ativos.
- Prometheus registry + Ehcache para categorias.

## Frontend (dms-frontend)

Repositório React/Vite complementar localizado em `../dms-frontend`. A UI consome `/v1/search/byCpf` e `/v1/documents/**` para exibir listagem, metadados e pré-visualização (PDF, imagem, texto).

Consulte o `README.md` do frontend para setup (`npm install`, `npm run dev`, `.env.local`).

---

### Próximos passos sugeridos

- Reimplementar buscas adicionais (por metadados/autores) em Mongo quando necessário.
- Adicionar testes slice (`@DataMongoTest`) específicos para a nova consulta por CPF.
- Consolidar um módulo comum (`dms-common`) caso surjam mais tipos compartilhados.
