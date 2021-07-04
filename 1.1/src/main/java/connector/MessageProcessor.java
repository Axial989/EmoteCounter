package connector;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.GuildEmoji;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.object.reaction.Reaction;
import sun.nio.ch.ThreadPool;

public class MessageProcessor 
{
	GatewayDiscordClient client;
	MessageCreateEvent originEvent;
	Map<GuildEmoji, Integer> emoteCounter;
	ExecutorService pool = Executors.newFixedThreadPool(1000000);
	
	public MessageProcessor (MessageCreateEvent event, GatewayDiscordClient client)
	{
		this.client = client;
		this.originEvent = event;
	}
	public class EmoteProcessorHelper implements Runnable
	{
		Message message;
		
		public EmoteProcessorHelper (Message m)
		{
			message = m;
		}

		@Override
		public void run() 
		{
			InverseSemaphore.increment();
			Set<Reaction> reactions = message.getReactions();
			System.out.println("Message sent at " + message.getTimestamp().toString() + "by " 
			+ message.getAuthor().map(user -> user.getUsername()));
			
			for (GuildEmoji emote: getEmotes(originEvent.getGuild().block()))
			{
				String temp = message.getContent(), emoteName = ":" + emote.getName() + ":";
				int counter = 0;
				while (temp.contains(emoteName))
				{
					counter++;
					int index = temp.indexOf(":" + emote.getName() + ":");
					temp = temp.substring(index + emoteName.length());
				}
				//---Processing of message content complete here
				
				for (Reaction r: reactions)
				{					
					if (r.getData().emoji().name().get().equals(emote.getName()))
						counter += r.getCount();
				}
				incrementCounter (emote, counter);
			}
			InverseSemaphore.decrement();			
		}		
	}
	
	public void processEmotes ()
	{
		Optional<Snowflake> guildID = originEvent.getGuildId();
		initialiseEmoteCounter();
		String re = "";
		for (GuildEmoji e: emoteCounter.keySet())
		{
			re+= e.getName() + "\n";
		}
		publishToChannel ("Extracted Emote List:\n" + re);
		
//		System.out.println(guildID.get());
//		client.getGuildChannels(guildID.get()).ofType(TextChannel.class);
		List<TextChannel> channels = getTextChannels(client.getGuildById(guildID.get()).block());
		System.out.println(channels.toString());
//		originEvent.getMessage().getRestChannel().createMessage(channels.toString());
		ArrayList<Runnable> runnableTasks = new ArrayList<Runnable> (channels.size());
		for (TextChannel channel: channels)
		{
			runnableTasks.add(() -> 
			processMessagesForEmotes(getAllMessagesFromChannel(channel))
			);
		}
		
		for (Runnable r: runnableTasks)
			pool.execute(r);
		try 
		{
			Thread.sleep(500000);
			
			while (InverseSemaphore.getCounter() != 0)
				System.err.println ("counter: " + InverseSemaphore.getCounter());
				Thread.sleep(500000);
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		Scanner scanner = new Scanner(System.in);
		while (true)
		{
			System.err.println ("Scanner warning:");
			String s = scanner.nextLine();
			if (s.contains("okay"))
				break;
		}
			pool.shutdown();
			try {
				while (!pool.awaitTermination(10000, TimeUnit.MILLISECONDS))
				Thread.sleep(10000);
				
			} catch (InterruptedException e) {
				
				e.printStackTrace();
			}
			publishToChannel(displayCounter("Results:"));
			
	}
	
	private void initialiseEmoteCounter ()
	{
		emoteCounter = new HashMap<GuildEmoji, Integer>();
		//Initialise all occurrences to 0 first
		for (GuildEmoji e: getEmotes(originEvent.getGuild().block()))
			emoteCounter.put(e, 0);
		displayCounter ("Initialised to:");
	}
	
	private String displayCounter (String title)
	{
		String response = title + "\n";
		response += String.format("%-20s %s\n", "Emote Name:", "Count:");
		for (GuildEmoji e: emoteCounter.keySet())
		{
//			response += "Emote: " + e.getName() + "\t\tCount: " + emoteCounter.get(e) + "\n";
			response += String.format("%-20s %s", e.getName(), emoteCounter.get(e)+"\n");

		}
		System.out.println(response);
		final String res = response;
		return res;
	}
	
	private void publishToChannel (final String result)
	{
		originEvent.getMessage().getChannel().flatMap(channel -> channel.createMessage(result)).subscribe();
	}
	
	private List<TextChannel> getTextChannels (Guild guild)
	{
		return guild.getChannels().ofType(TextChannel.class).collectList().block();
	}
	
	private List<Message> getAllMessagesFromChannel (TextChannel channel)
	{
		Snowflake now = Snowflake.of(Instant.now());
		return channel.getMessagesBefore(now).collectList().block();		
	}
	
	private void processMessagesForEmotes (List<Message> messages)
	{
//		List<GuildEmoji> emojis = getEmotes(messages.get(0).getGuild().block());
//		for (GuildEmoji emoji : emojis)
//			System.out.print(emoji.getName() + ":" + emoji.getId() + ", ");
//		System.out.println ("Messages in channel: " + 
//			messages.get(0).getChannelId().toString());
		for (Message m: messages)
//			System.out.println (m.getContent());
			pool.execute(new EmoteProcessorHelper(m));
		
		File file = new File ("C:/Users/allia/Desktop/file" + messages.get(0).getChannelId() +".txt");
		try {
			PrintWriter writer = new PrintWriter (file);
			writer.append(displayCounter(Date.from(Instant.now()).toString()));
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	private List<GuildEmoji> getEmotes (Guild guild)
	{
		return guild.getEmojis().collectList().block();
	}
	
	private synchronized void incrementCounter (GuildEmoji key, Integer byNumber)
	{
		emoteCounter.replace(key, emoteCounter.get(key) + byNumber);
	}
}
