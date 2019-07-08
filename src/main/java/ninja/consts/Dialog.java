package ninja.consts;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

public enum Dialog {
	WEATHER( "district", "hours" ), BUS( "stop" ), STATION( "start", "end" );

	private final String[] key;

	private Dialog( String... key ) {
		this.key = key;
	}

	public String text( Map<String, String> submission ) {
		return Arrays.stream( key ).map( submission::get ).collect( Collectors.joining( StringUtils.SPACE ) );
	}
}