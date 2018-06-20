package asylum.NurseBot.utils;

import org.telegram.telegrambots.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.api.objects.ChatMember;
import org.telegram.telegrambots.api.objects.User;
import org.telegram.telegrambots.exceptions.TelegramApiException;

import asylum.NurseBot.NurseNoakes;
import asylum.NurseBot.objects.Permission;

public class SecurityChecker {
	private NurseNoakes nurse;
	
	public SecurityChecker(NurseNoakes nurse) {
		this.nurse = nurse;
	}
	
	public Permission getRole(Long chatid, User user) throws TelegramApiException {
		GetChatMember getChatMember = new GetChatMember();
		getChatMember.setChatId(chatid);
		getChatMember.setUserId(user.getId());
		
		ChatMember member = nurse.execute(getChatMember);
		
		String status = member.getStatus();
		
		switch (status) {
		case "creator":
			return Permission.OWNER;
		case "administrator":
			return Permission.ADMIN;
		case "member":
			return Permission.USER;
		default: // left, kicked
			System.out.println("User has status " + status);
			return Permission.USER;
		}	
	}
	
	public boolean checkRights(Long chatid, User user, Permission permission) throws TelegramApiException {
		return getRole(chatid, user).compare(permission) >= 0;
	}
}
