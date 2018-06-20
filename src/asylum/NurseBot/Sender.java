package asylum.NurseBot;

import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.User;
import org.telegram.telegrambots.exceptions.TelegramApiException;

import asylum.NurseBot.utils.StringTools;

public class Sender {

	private Long chatid;
	private NurseNoakes nurse;
	
	public Sender(Long chatId, NurseNoakes nurse) {
		this.chatid = chatId;
		this.nurse = nurse;
	}

	public void mention(User user, String text) throws TelegramApiException {
		send(StringTools.makeMention(user) + " " + text, true);
	}
	
	public void reply(String text, Message replyto) throws TelegramApiException {
		send(text, false, replyto);
	}
	
	public void reply(String text, Message replyto, boolean markdown) throws TelegramApiException {
		send(text, markdown, replyto);
	}
	
	public void send(String text) throws TelegramApiException {
		send(text, false, null);
	}
	
	public void send(String text, boolean markdown) throws TelegramApiException {
		send(text, markdown, null);
	}
	
	public void send(String text, boolean markdown, Message replyto) throws TelegramApiException {
		SendMessage message = new SendMessage();
		if (markdown)
			message.setParseMode("markdown");
		if (replyto != null)
			message.setReplyToMessageId(replyto.getMessageId());
		message.setChatId(chatid);
		message.setText(text);
		nurse.execute(message);
	}
}