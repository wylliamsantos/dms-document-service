package br.com.dms.exception;

import java.io.Serializable;

public class DefaultError implements Serializable {

	private static final long serialVersionUID = -812241549989262850L;

	private String mensagem;
	private TypeException tipo;
	private String transactionId;

	public DefaultError() {

	}

	public DefaultError(String message, TypeException type, String transactionId) {
		super();
		this.mensagem = message;
		this.tipo = type;
		this.transactionId = transactionId;
	}

	public String getMensagem() {
		return mensagem;
	}

	public void setMessage(String message) {
		this.mensagem = message;
	}

	public TypeException getTipo() {
		return tipo;
	}

	public void setType(TypeException type) {
		this.tipo = type;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}

}