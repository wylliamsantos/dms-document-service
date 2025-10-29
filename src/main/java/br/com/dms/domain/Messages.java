package br.com.dms.domain;

public abstract class Messages {

    public final static String CATEGORY_EXISTS = "Categoria já cadastrada com esse nome";
    public final static String BAD_REQUEST = "Não foi possível processar a solicitação, dados de requisição inválido";
    public final static String CATEGORY_NOT_FOUND = "Categoria não encontrada";
    public final static String CATEGORY_INACTIVE = "Categoria inativa";
    public final static String ISSUING_DATE_IS_MANDATORY = "Data de emissão/referência obrigatória para o tipo de documento informado";
    public final static String INVALID_DATE = "Data inválida";
    public final static String DOCUMENT_VERSION_NOT_FOUND = "Versão do documento não encontrada";
    public final static String DOCUMENT_UPLOAD_ALREADY_FINALIZED = "Upload do documento já finalizado";
    public final static String DOCUMENT_UPLOAD_NOT_FOUND = "Arquivo não encontrado no armazenamento";
    public final static String DOCUMENT_UPLOAD_PATH_MISSING = "Caminho do documento não configurado";
    public final static String DOCUMENT_UPLOAD_SIZE_MISMATCH = "Tamanho do arquivo divergente do informado";
}
