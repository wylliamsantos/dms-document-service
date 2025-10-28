## dms-document-service

Serviço responsável pela ingestão, atualização e consulta de documentos no DMS. Persistimos metadados em MongoDB, binários em S3 compatível e cacheamos informações derivadas em Redis.

### Build & Test

- `./gradlew compileJava` — valida o projeto com Java 21.
- `./gradlew test` — executa a suíte de testes.
- `./gradlew bootRun` — sobe a aplicação localmente (Mongo/Redis/S3 devem estar acessíveis).

### Endpoints principais (todos sob `/v1/documents`)

- `POST /` (multipart ou `/base64`) — cria documentos; controla versão automática (major/minor) e assinatura digital quando configurada.
- `GET /{documentId}/information` — retorna metadados da última versão (ou `/ {documentId}/{version}/information`).
- `GET /{documentId}/versions` — lista histórico de versões.
- `GET /{documentId}/content` e `/base64` — recupera conteúdo (binário ou Base64).
- `POST /{documentId}` — cria nova versão via multipart (major/minor).
- `DELETE /{documentId}` — remove documento e limpa caches.

### Detalhes de implementação relevantes

- Versões (`DmsDocumentVersion`) passam a salvar `modifiedAt` na criação inicial, alinhando histórico para o frontend.
- CORS configurável via `dms.cors.*` em `application.yml` / variáveis `DMS_CORS_ALLOWED_ORIGINS` etc.
- Enum `VersionType` agora compartilhado em `br.com.dms.domain.core`, reutilizado por ambos os serviços.

### Execução local

1. Exportar Java 21 (GraalVM local):
   ```bash
   export JAVA_HOME=/Users/wylliamsantos/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.6/Contents/Home
   export PATH="$JAVA_HOME/bin:$PATH"
   ```
2. Garantir MongoDB, Redis e S3 (MinIO) disponíveis conforme `application-local.yml`.
3. `./gradlew bootRun`.

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
