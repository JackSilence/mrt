package ninja.slack;

public class Event {
	private String type, text;

	public String getType() {
		return type;
	}

	public void setType( String type ) {
		this.type = type;
	}

	public String getText() {
		return text;
	}

	public void setText( String text ) {
		this.text = text;
	}
}