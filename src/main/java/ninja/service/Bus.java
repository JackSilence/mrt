package ninja.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.http.client.fluent.Request;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.gson.reflect.TypeToken;

import magic.util.Utils;
import ninja.util.Cast;
import ninja.util.Gson;
import ninja.util.Jsoup;
import ninja.util.Signature;

@Service
public class Bus {
	private static final String AUTH_HEADER = "hmac username=\"%s\", algorithm=\"hmac-sha1\", headers=\"x-date\", signature=\"%s\"";

	private static final String API_URL = "https://ptx.transportdata.tw/MOTC/v2/Bus/%s/City/Taipei/%s?$format=JSON&%s";

	private static final String ROUTES_URL = "https://ebus.gov.taipei/EBus/RouteList?ct=tpc", ROUTE_ID_REGEX = "javascript:go\\('(.+?)'\\)";

	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern( "EEE, dd MMM yyyy HH:mm:ss z", Locale.US );

	private static final Map<String, String> ROUTES = new HashMap<>();

	@Value( "${ptx.app.id:}" )
	private String id;

	@Value( "${ptx.app.key:}" )
	private String key;

	public List<Map<String, ?>> call( String method, String route, String... query ) {
		Request request = Request.Get( String.format( API_URL, method, route, String.join( "&", query ) ) );

		String xdate = ZonedDateTime.now( ZoneId.of( "GMT" ) ).format( DATE_TIME_FORMATTER );

		String signature = Base64.getEncoder().encodeToString( Signature.hmac( "x-date: " + xdate, key, HmacAlgorithms.HMAC_SHA_1 ) );

		request.addHeader( "Authorization", String.format( AUTH_HEADER, id, signature ) ).addHeader( "x-date", xdate );

		return Gson.from( Utils.getEntityAsString( request.addHeader( "Accept-Encoding", "gzip" ) ), new TypeToken<List<?>>() {
		}.getType() );
	}

	public String stop( Map<?, ?> map ) {
		return Cast.string( Cast.map( map, "StopName" ), "Zh_tw" );
	}

	public String id( String route ) {
		return ROUTES.get( route );
	}

	public boolean check( String route ) {
		if ( ROUTES.isEmpty() ) {
			init();
		}

		return ROUTES.containsKey( route );
	}

	@PostConstruct
	private void init() {
		Jsoup.select( ROUTES_URL, "section.busline li > a", i -> {
			Matcher matcher = Pattern.compile( ROUTE_ID_REGEX ).matcher( i.attr( "href" ) );

			if ( matcher.find() ) {
				ROUTES.put( i.text(), matcher.group( 1 ) );
			}
		} );
	}
}