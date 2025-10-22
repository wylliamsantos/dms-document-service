package br.com.dms.domain.mongodb;

import java.io.Serializable;

public class DocumentId implements Serializable {

	private static final long serialVersionUID = 1L;

	public DocumentId(String id, String version) {
		super();
		this.id = id;
		this.version = version;
	}

	public DocumentId() {
	}

	private String id;
	private String version;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	@Override
	public String toString() {
		return "DocumentId{" +
				"id='" + id + '\'' +
				", version='" + version + '\'' +
				'}';
	}
}
