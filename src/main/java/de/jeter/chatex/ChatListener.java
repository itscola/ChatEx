package de.jeter.chatex;

import de.jeter.chatex.api.events.MessageBlockedByAntiSpamManagerEvent;
import de.jeter.chatex.api.events.DefaultEventManager;
import de.jeter.chatex.plugins.PluginManager;
import de.jeter.chatex.utils.*;
import de.jeter.chatex.utils.adManager.AdManager;
import de.jeter.chatex.utils.adManager.SimpleAdManager;
import de.jeter.chatex.utils.adManager.SmartAdManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.UnknownFormatConversionException;

/**
 * @author TheJeterLP
 */
public class ChatListener implements Listener {
    private AdManager adManager = Config.ADS_SMART_MANAGER.getBoolean() ? new SmartAdManager() : new SimpleAdManager();

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(final AsyncPlayerChatEvent event) {
        if (!event.getPlayer().hasPermission("chatex.allowchat")) {
            String msg = Locales.COMMAND_RESULT_NO_PERM.getString(event.getPlayer()).replaceAll("%perm", "chatex.allowchat");
            event.getPlayer().sendMessage(msg);
            event.setCancelled(true);
            return;
        }

        if (!AntiSpamManager.isAllowed(event.getPlayer())) {
            String message = Locales.ANTI_SPAM_DENIED.getString(event.getPlayer()).replaceAll("%time%", AntiSpamManager.getRemaingSeconds(event.getPlayer()) + "");
            long remainingTime = AntiSpamManager.getRemaingSeconds(event.getPlayer());
            event.getPlayer().sendMessage(message);
            MessageBlockedByAntiSpamManagerEvent messageBlockedByAntiSpamManagerEvent = new MessageBlockedByAntiSpamManagerEvent(event.getPlayer(),message, event.getMessage(),remainingTime);
            DefaultEventManager.getInstance().handleEvent(messageBlockedByAntiSpamManagerEvent);
            event.setCancelled(messageBlockedByAntiSpamManagerEvent.isCancelled());
            return;
        }
        AntiSpamManager.put(event.getPlayer());

        String format = PluginManager.getInstance().getMessageFormat(event.getPlayer());
        Player player = event.getPlayer();
        String chatMessage = event.getMessage();

        if (adManager.checkForAds(chatMessage, player)) {
            event.getPlayer().sendMessage(Locales.MESSAGES_AD.getString(null).replaceAll("%perm", "chatex.bypassads"));
            event.setCancelled(true);
            return;
        }

        if (Utils.checkForBlocked(event.getMessage())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Locales.MESSAGES_BLOCKED.getString(null));
            return;
        }

        boolean global = false;
        if (Config.RANGEMODE.getBoolean() || Config.BUNGEECORD.getBoolean()) {
            if (chatMessage.startsWith("!")) {
                if (player.hasPermission("chatex.chat.global")) {
                    chatMessage = chatMessage.replaceFirst("!", "");
                    format = PluginManager.getInstance().getGlobalMessageFormat(event.getPlayer());
                    global = true;
                } else {
                    player.sendMessage(Locales.COMMAND_RESULT_NO_PERM.getString(player).replaceAll("%perm", "chatex.chat.global"));
                    event.setCancelled(true);
                    return;
                }
            } else {
                if (Config.RANGEMODE.getBoolean()) {
                    event.getRecipients().clear();
                    if (Utils.getLocalRecipients(player).size() == 1 && Config.SHOW_NO_RECEIVER_MSG.getBoolean()) {
                        player.sendMessage(Locales.NO_LISTENING_PLAYERS.getString(player));
                        event.setCancelled(true);
                        return;
                    } else {
                        event.getRecipients().addAll(Utils.getLocalRecipients(player));
                    }
                }
            }
        }

        if (global && Config.BUNGEECORD.getBoolean()) {
            String msgToSend = Utils.replacePlayerPlaceholders(player, format.replaceAll("%message", Utils.translateColorCodes(chatMessage, player)));
            ChannelHandler.getInstance().sendMessage(player, msgToSend);
        }

        format = format.replace("%message", "%2$s");
        format = Utils.replacePlayerPlaceholders(player, format);

        try {
            event.setFormat(format);
        } catch (UnknownFormatConversionException ex) {
            ChatEx.getInstance().getLogger().severe("Placeholder in format is not allowed!");
            format = format.replaceAll("%\\\\?.*?%", "");
            event.setFormat(format);
        }

        event.setMessage(Utils.translateColorCodes(chatMessage, player));
        ChatLogger.writeToFile(event.getPlayer(), event.getMessage());
    }

}
