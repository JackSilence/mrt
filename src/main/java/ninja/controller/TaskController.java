package ninja.controller;

import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.gpedro.integrations.slack.SlackAction;
import ninja.consts.Task;
import ninja.slack.Payload;
import ninja.util.Gson;
import ninja.util.Heroku;

@RestController
@RequestMapping( "/task" )
public class TaskController extends BaseController {
	private static final String METHOD = "chat.command", QUERY = "&command=/%s&text=%s";

	private enum Type {
		INTERACTIVE_MESSAGE, DIALOG_SUBMISSION;
	}

	@PostMapping
	public String task() {
		return Heroku.task().toString();
	}

	@PostMapping( "/execute" )
	public void execute( String payload ) {
		Payload message = Gson.from( payload, Payload.class );

		Type type = EnumUtils.getEnumIgnoreCase( Type.class, message.getType() );

		Assert.notNull( type, payload );

		String id = message.getId(), command, text = StringUtils.EMPTY;

		if ( Type.INTERACTIVE_MESSAGE.equals( type ) ) {
			check( Heroku.TASK_ID, id, payload );

			List<SlackAction> actions = message.getActions();

			Assert.isTrue( CollectionUtils.isNotEmpty( actions ) && actions.size() == 1, payload );

			SlackAction action = actions.get( 0 );

			check( Heroku.TASK_ID, action.getName(), payload );

			command = Task.valueOf( action.getValue() ).name().toLowerCase(); // 可確定Task存在

		} else {
			check( "weather", command = id, payload );

			Map<String, String> submission = message.getSubmission();

			Assert.notEmpty( submission, payload );

			text = submission.get( "district" ) + StringUtils.SPACE + submission.get( "hours" );
		}

		String token = System.getenv( "slack.legacy.token." + message.getUser().getName() );

		// 使用legacy token執行command, 只有對應的帳號才會看到return message
		log.info( get( METHOD, token, message.getChannel().getId(), String.format( QUERY, command, text ) ) );
	}

	private void check( String expected, String actual, String payload ) {
		Assert.isTrue( expected.equals( actual ), payload );
	}
}