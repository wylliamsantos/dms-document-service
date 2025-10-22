package br.com.dms.exception;

/**
 * Represents a business exception
 * 
 *
 */
public class DmsDocumentNotFoundException extends DmsException {

	private static final long serialVersionUID = -9087912945651122075L;

	public DmsDocumentNotFoundException(String shortMessage, TypeException type, String transactionId) {
		super(shortMessage, type, transactionId);
	}
	
	public DmsDocumentNotFoundException(String shortMessage, TypeException type) {
		super(shortMessage, type);
	}

}
