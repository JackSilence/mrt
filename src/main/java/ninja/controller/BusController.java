package ninja.controller;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import net.gpedro.integrations.slack.SlackAttachment;
import net.gpedro.integrations.slack.SlackMessage;
import ninja.consts.Filter;
import ninja.service.Bus;
import ninja.util.Cast;
import ninja.util.Check;
import ninja.util.Slack;

@RestController
public class BusController extends DialogController {
	private static final String WEB_URL = "https://ebus.gov.taipei/EBus/VsSimpleMap?routeid=%s&gb=0";

	private static final Map<Double, String> STATUS = ImmutableMap.of( 1d, "尚未發車", 2d, "交管不停靠", 3d, "末班車已過", 4d, "今日未營運" );

	@Autowired
	private Bus bus;

	@PostMapping( "/bus" )
	public String bus( @RequestParam String command, @RequestParam String text ) {
		String[] params = text.contains( StringUtils.SPACE ) ? Check.params( text ) : ArrayUtils.toArray( text, StringUtils.EMPTY );

		String route = params[ 0 ], stop = params[ 1 ], unwrap = bus.unwrap( stop ), filter;

		Check.expr( bus.check( route ), "查無路線: " + route );

		Map<String, ?> info = bus.call( "Route", filter = Filter.ROUTE.eq( route ) ).get( 0 ); // 原則上不可能拿不到

		String departure = Cast.string( info, "DepartureStopNameZh" ), destination = Cast.string( info, "DestinationStopNameZh" );

		SlackAttachment attach = Slack.attachment().setTitle( route + "公車動態" ).setTitleLink( String.format( WEB_URL, bus.id( route ) ) );

		SlackMessage message = Slack.message( attach, command, text );

		if ( stop.isEmpty() ) {
			return message( message );
		}

		filter = Filter.and( filter, stop.equals( unwrap ) ? Filter.STOP.contains( stop ) : Filter.STOP.eq( unwrap ), Filter.DIRECTION.le( "1" ) );

		bus.call( "EstimatedTimeOfArrival", filter, "$orderby=Direction" ).stream().collect( Collectors.groupingBy( bus::stop, Collectors.toList() ) ).forEach( ( k, v ) -> {
			message.addAttachments( Slack.attachment( "good" ).setText( ":busstop:" + k ).setFields( list( v.stream().map( i -> {
				Double direction = ( Double ) i.get( "Direction" ), time = ( Double ) i.get( "EstimateTime" ), status = ( Double ) i.get( "StopStatus" );

				return field( "往".concat( direction.equals( 0d ) ? destination : departure ), time == null ? STATUS.get( status ) : time( time ) );

			} ) ) ) );
		} );

		return message( message );
	}

	@PostMapping( "/station" )
	public String station( @RequestParam( CHANNEL_ID ) String channel, @RequestParam( "user_name" ) String user, @RequestParam String command, @RequestParam String text ) {
		String[] params = Check.params( text );

		String start = params[ 0 ], end = params[ 1 ], filter = Filter.or( Filter.STATION.eq( start ), Filter.STATION.eq( end ) );

		Check.expr( !start.equals( end ), "起訖站不得相同: " + start );

		Map<String, Set<String>> info = bus.call( "Station", filter ).stream().collect( Collectors.toMap( bus::station, i -> {
			return bus.stops( i, j -> bus.name( j, "RouteName" ) ).collect( Collectors.toSet() );

		}, Sets::union ) );

		Check.expr( info.keySet().size() == 2, "查無起站或訖站: " + text );

		Sets.intersection( info.get( start ), info.get( end ) ).parallelStream().forEach( i -> command( user, channel, "bus", bus.text( i, start ) ) );

		return message( Slack.attachment(), command, text );
	}

	private String time( Double time ) {
		int seconds = time.intValue(), minutes = seconds / 60;

		return ( minutes > 0 ? minutes + "分" : StringUtils.EMPTY ) + seconds % 60 + "秒";
	}
}