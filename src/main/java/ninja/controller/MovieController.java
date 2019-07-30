package ninja.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import net.gpedro.integrations.slack.SlackActionType;
import net.gpedro.integrations.slack.SlackAttachment;
import net.gpedro.integrations.slack.SlackField;
import ninja.consts.Act;
import ninja.slack.Action;
import ninja.slack.Confirm;
import ninja.util.Jsoup;
import ninja.util.Slack;
import ninja.util.Utils;

@RestController
public class MovieController extends DialogController {
	private static final String URL = "http://www.atmovies.com.tw", PATH = "/showtime/a02/", RATING_REGEX = "/images/cer_(.+?).gif";

	private static final Map<String, Map<String, String>> THEATERS = new LinkedHashMap<>();

	private static final Map<String, String> RATINGS = new HashMap<>();

	static {
		RATINGS.put( "G", "普遍級" );
		RATINGS.put( "P", "保護級" );
		RATINGS.put( "PG", "輔導級" );
		RATINGS.put( "F2", "輔12級" );
		RATINGS.put( "F5", "輔15級" );
		RATINGS.put( "R", "限制級" );
	}

	@Value( "${movie.icon.url:}" )
	private String url;

	@Override
	protected Object[] args() {
		return ArrayUtils.toArray( json( THEATERS.entrySet().stream().map( i -> {
			return ImmutableMap.of( LABEL, i.getKey(), OPTIONS, i.getValue().keySet().stream().map( super::option ).collect( Collectors.toList() ) );
		} ) ) );
	}

	@PostMapping( "/theater" )
	public String theater( @RequestParam String text ) {
		Action action = new Action( Act.MOVIE, "請選擇要觀看的電影", SlackActionType.SELECT, null ).setConfirm( new Confirm() );

		theater( text, i -> action.addOption( option( title( i ), text ) ) );

		return message( Slack.message().addAttachments( Slack.attachment( Act.MOVIE ).addAction( action ) ) );
	}

	@PostMapping( "/movie" )
	public String movie( @RequestParam String command, @RequestParam String text ) {
		String[] params = StringUtils.split( text );

		check( params.length == 2, "參數個數有誤: " + text );

		String theater = params[ 0 ], film = params[ 1 ];

		SlackAttachment attach = Slack.attachment().setAuthorName( theater ).setAuthorIcon( url ).setTitle( film );

		List<SlackField> fields = new ArrayList<>();

		theater( theater, i -> {
			Element title = title( i ), ul = i.child( 1 ).child( 0 ), li = ul.child( 0 );

			if ( film.equals( title.text() ) ) {
				String txt = ul.nextElementSibling().select( "li:not(.filmVersion,.theaterElse)" ).text().replace( "：", ":" );

				String version = i.select( "li.filmVersion" ).text(), time = StringUtils.EMPTY;

				if ( version.isEmpty() ) {
					time = StringUtils.replace( txt, StringUtils.SPACE, "|" );

				} else {
					String[] arr = StringUtils.split( txt );

					for ( int j = 0; j < arr.length; j++ ) {
						time += j % 3 == 1 ? "|" + arr[ j ] : j % 3 == 2 ? "|" + arr[ j ] + "\n" : arr[ j ];
					}
				}

				if ( fields.isEmpty() ) {
					attach.setAuthorLink( i.baseUri() ).setTitleLink( Jsoup.href( link( li ) ) ).setImageUrl( src( li ) );

					attach.setColor( star( title ) ? "good" : null );

					String rating = RATINGS.get( Utils.find( RATING_REGEX, src( li = li.nextElementSibling() ) ) );

					attach.setText( String.format( "`%s` `%s`", rating, StringUtils.remove( li.text(), "片長：" ) ) );
				}

				fields.add( field( version, time ) );
			}
		} );

		Assert.notEmpty( fields, "查無影片: " + film );

		return message( attach.setFields( fields ), command, text );
	}

	private void theater( String theater, Consumer<? super Element> action ) {
		Jsoup.select( URL + checkNull( THEATERS.values().stream().flatMap( i -> i.entrySet().stream() ).filter( i -> {
			return i.getKey().equals( theater );

		} ).map( Entry::getValue ).findFirst().orElse( null ), "查無影院: " + theater ), "ul#theaterShowtimeTable", action );
	}

	private Map<String, String> option( Element title, String theater ) {
		String film = title.text(), star = star( title ) ? "★" : StringUtils.EMPTY;

		return ImmutableMap.of( TEXT, star + film, VALUE, Utils.spacer( theater, film ) );
	}

	private boolean star( Element title ) {
		return src( title ).contains( "star" );
	}

	private String src( Element element ) {
		return element.selectFirst( "img" ).attr( "src" );
	}

	private Element link( Element element ) {
		return element.selectFirst( "a[href]" );
	}

	private Element title( Element element ) {
		return element.selectFirst( "li.filmTitle" );
	}

	@PostConstruct
	public void init() {
		Jsoup.select( URL + PATH, "ul#theaterList > li", i -> {
			if ( i.hasClass( "type0" ) ) {
				THEATERS.put( StringUtils.remove( i.text(), "▼" ), new LinkedHashMap<>() );
			} else {
				Element link = link( i );

				THEATERS.get( Iterables.getLast( THEATERS.keySet() ) ).put( link.text(), Jsoup.href( link ) );
			}
		} );
	}
}