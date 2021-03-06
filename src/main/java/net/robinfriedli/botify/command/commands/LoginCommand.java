package net.robinfriedli.botify.command.commands;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.LoggerFactory;

import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.UserException;
import net.robinfriedli.botify.login.Login;
import net.robinfriedli.botify.login.LoginManager;

public class LoginCommand extends AbstractCommand {

    public LoginCommand(CommandContribution commandContribution, CommandContext commandContext, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, commandContext, commandManager, commandString, false, identifier, description, Category.SPOTIFY);
    }

    @Override
    public void doRun() {
        User user = getContext().getUser();
        AuthorizationCodeUriRequest uriRequest = getContext().getSpotifyApi().authorizationCodeUri()
            .show_dialog(true)
            .state(user.getId())
            .scope("playlist-read-private playlist-read-collaborative user-library-read playlist-modify-private playlist-modify-public")
            .build();

        LoginManager loginManager = Botify.get().getLoginManager();
        CompletableFuture<Login> pendingLogin = new CompletableFuture<>();
        loginManager.expectLogin(user, pendingLogin);

        String response = String.format("Your login link:\n%s", uriRequest.execute().toString());
        CompletableFuture<Message> futureMessage = sendMessage(user, response);
        try {
            futureMessage.get();
            sendMessage("I sent you a login link");
        } catch (CancellationException | ExecutionException e) {
            loginManager.removePendingLogin(user);
            throw new UserException("I was unable to send you a message. Please adjust your privacy settings to allow direct messages from guild members.");
        } catch (InterruptedException ignored) {
        }

        pendingLogin.orTimeout(10, TimeUnit.MINUTES).whenComplete((login, throwable) -> {
            if (login != null) {
                sendSuccess("User " + getContext().getUser().getName() + " logged in to Spotify");
            }
            if (throwable != null) {
                loginManager.removePendingLogin(user);

                if (throwable instanceof TimeoutException) {
                    sendMessage(user, "Login attempt timed out");
                    setFailed(true);
                } else {
                    getMessageService().sendException("There has been an unexpected error while completing your login, please try again.", getContext().getChannel());
                    LoggerFactory.getLogger(getClass()).error("unexpected exception while completing login", throwable);
                    setFailed(true);
                }
            }
        });
    }

    @Override
    public void onSuccess() {
    }

}
