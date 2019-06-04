package ninja.util;

import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import ninja.slack.Payload;

public class Gson {
	private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

	public static Map<String, Object> map( JsonObject object ) {
		return map( GSON.toJson( object ) );
	}

	public static Map<String, Object> map( String json ) {
		return GSON.fromJson( json, new TypeToken<Map<String, Object>>() {
		}.getType() );
	}

	public static Payload payload( String json ) {
		return GSON.fromJson( json, Payload.class );
	}
}