package br.com.dms.exception;

import org.apache.commons.lang3.StringUtils;

/**
 * Represents a exception
 * 
 *
 */
public class DmsException extends RuntimeException {

	private static final long serialVersionUID = -9087912945651122075L;

	private final String message;
	private final TypeException type;
	private final String transactionId;

	/**
	 * 
	 * @param message
	 *            the short message
	 * 
	 * @param type
	 *            the type of exception eg., CONF, VALID...
	 */
	public DmsException(String shortMessage,TypeException type, String transactionId) {
		this.message = shortMessage;
		this.type = type;
		this.transactionId = transactionId;
	}
	
	/**
	 * 
	 * @param message
	 *            the short message
	 * 
	 * @param type
	 *            the type of exception eg., CONF, VALID...
	 */
	public DmsException(String shortMessage,TypeException type) {
		this.message = shortMessage;
		this.type = type;
		this.transactionId = StringUtils.EMPTY;
	}

	@Override
	public String getMessage() {
		return message;
	}

	public TypeException getType() {
		return type;
	}

	public String getTransactionId() {
		return transactionId;
	}

}