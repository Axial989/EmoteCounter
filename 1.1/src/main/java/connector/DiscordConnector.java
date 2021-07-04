package connector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import discord4j.common.util.Snowflake;
import discord4j.core.*;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.User;

public class DiscordConnector 
{
	GatewayDiscordClient client;
	String token;
	final static String architectUsername = "Darkstar", 
			architectDiscriminator = "3404";
	private static final Map<String, Command> commands = new HashMap<>();
	
	public DiscordConnector (String token)
	{
		this.token = token;
		initialise();
		listenToGatewayConnect();
		addCommands();
		listenForMessages();
	}
	
	private boolean masterCheck (MessageCreateEvent event)
	{
		 String username = event.getMessage().getUserData().username();
	     String discriminator = event.getMessage().getUserData().discriminator();
	     System.out.println ("Message author: " + username + "#" + discriminator);
	     
	     return username.equals(architectUsername) && discriminator.equals(architectDiscriminator);
	}
	
	private void initialise ()
	{
		client = DiscordClientBuilder.create(token)
				.build()
				.login()
				.block();
	}
	
	private void listenToGatewayConnect ()
	{
		 client.getEventDispatcher().on(ReadyEvent.class)
	        .subscribe(event -> {
	            final User self = event.getSelf();
	            System.out.println(String.format(
	                "Logged in as %s#%s", self.getUsername(), self.getDiscriminator()));
	        });
	}
	
	private void addCommands ()
	{
		commands.put("Hello, Sae", event -> event.getMessage().getChannel()
        .flatMap(channel -> channel.createMessage("Hello, Aegis. I am in operating condition.")).subscribe());
//		commands.put("ping", event -> event)
		
		commands.put("get last message",
			event ->  {
				Snowflake messageId = event.getMessage().getId();
//				Mono<MessageChannel> channel = event.getMessage().getRestChannel();
				
				List<String> messages = event.getMessage().getRestChannel()
						.getMessagesBefore(messageId)
						.take(5).map(message -> message.content()).collectList().block();
//				String data = 
//						event.getMessage().getRestChannel()
//						.getMessagesBefore(messageId).take(5).map(flux -> flux.content());
//				System.out.println("System data print: " + data.toString());				
				event.getMessage().getRestChannel().createMessage(messages.toString()).block();
			});
		
		commands.put ("Process emotes", event -> {
			MessageProcessor processor = new MessageProcessor(event, client);
			processor.processEmotes();
			
		});
	}
	
	private void listenForMessages ()
	{
//		client.getEventDispatcher().on(MessageCreateEvent.class)
//        .map(MessageCreateEvent::getMessage)
//        .filter(message -> message.getAuthor().map(user -> (user.getUsername().equals("Darkstar") && user.getDiscriminator().equals("3404"))).orElse(false))
//        .filter(message -> message.getContent().equalsIgnoreCase("!ping"))
//        .flatMap(Message::getChannel)
//        .flatMap(channel -> channel.createMessage("Pong!"))
//        .subscribe();
		
		client.getEventDispatcher().on(MessageCreateEvent.class)
	    // subscribe is like block, in that it will *request* for action
	    // to be done, but instead of blocking the thread, waiting for it
	    // to finish, it will just execute the results asynchronously.
	    .subscribe(event -> {
	        // 3.1 Message.getContent() is a String
	        final String content = event.getMessage().getContent();
	        for (final Map.Entry<String, Command> entry : commands.entrySet()) {
	            if (content.contains(entry.getKey()) && masterCheck (event)) {
	            	System.out.println("Execution of " + entry.getKey());
	                entry.getValue().execute(event);
	                break;
	            }
	        }
	    });
		
		client.onDisconnect().block();
		
	}

	public static void main(String[] args) 
	{
		DiscordConnector connector = new DiscordConnector ("");
	}

}
