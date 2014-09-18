
package demo;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

@Entity
public class Photo {

	@Id
	@GeneratedValue
	private Long id;

	private String fileRef;

	public Photo(String fileRef) {
		this.fileRef = fileRef;
	}

	public Long getId() {
		return this.id;
	}

	public String getFileRef() {
		return this.fileRef;
	}

}