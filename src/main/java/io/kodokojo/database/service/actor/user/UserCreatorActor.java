/**
 * Kodo Kojo - Microservice which allow to access to Database.
 * Copyright © 2017 Kodo Kojo (infos@kodokojo.io)
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package io.kodokojo.database.service.actor.user;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import io.kodokojo.commons.RSAUtils;
import io.kodokojo.commons.config.ApplicationConfig;
import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.event.payload.UserCreationReply;
import io.kodokojo.commons.model.Organisation;
import io.kodokojo.commons.model.User;
import io.kodokojo.commons.model.UserInWaitingList;
import io.kodokojo.commons.service.EmailSender;
import io.kodokojo.commons.service.actor.EmailSenderActor;
import io.kodokojo.commons.service.actor.message.EventUserReplyMessage;
import io.kodokojo.commons.service.actor.message.EventUserRequestMessage;
import io.kodokojo.commons.service.repository.UserRepository;
import io.kodokojo.database.service.actor.EndpointActor;
import io.kodokojo.database.service.actor.organisation.OrganisationCreatorActor;
import io.kodokojo.database.service.actor.organisation.OrganisationMessage;
import org.apache.commons.lang.StringUtils;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static akka.event.Logging.getLogger;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang.StringUtils.isBlank;

public class UserCreatorActor extends AbstractActor {

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    public static Props PROPS(UserRepository userRepository, ApplicationConfig applicationConfig) {
        requireNonNull(userRepository, "userRepository must be defined.");
        requireNonNull(applicationConfig, "applicationConfig must be defined.");
        return Props.create(UserCreatorActor.class, userRepository, applicationConfig);
    }

    private final UserRepository userRepository;

    private final ApplicationConfig applicationConfig;

    private boolean isValid = false;

    private KeyPair keyPair;

    private String password = "";

    private String organisationId;

    private boolean addUserToOrganisation;

    private EventUserCreateMsg message;

    private ActorRef originalActor;

    public UserCreatorActor(UserRepository userRepository, ApplicationConfig applicationConfig) {
        this.userRepository = userRepository;
        this.applicationConfig = applicationConfig;
        addUserToOrganisation = false;
        receive(ReceiveBuilder.match(EventUserCreateMsg.class, this::onCreateUserRequest)
                .match(OrganisationCreatorActor.OrganisationCreatedResultMsg.class, this::onOrganisationCreated)
                .match(UserEligibleActor.UserEligibleResultMsg.class, this::onUserIsEligible)
                .match(UserGenerateSecurityData.UserSecurityDataMsg.class, this::onSecurityDataGenerated)
                .build()
        );
    }

    private void onCreateUserRequest(EventUserCreateMsg u) {
        originalActor = sender();
        message = u;
        if (applicationConfig.userCreationRoutedInWaitingList() && message.getRequester() == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Creation of User '{}' send to waiting list.", message.getUsername());
            }
            UserInWaitingList userInWaitingList = new UserInWaitingList(message.getUsername(), message.getEmail(), System.currentTimeMillis());
            userRepository.addUserToWaitingList(userInWaitingList);
            originalActor.tell(new UserInWaitinglistResultMsg(null, message.originalEvent(), userInWaitingList), self());
            getContext().stop(self());
        } else {
            getContext().actorOf(UserGenerateSecurityData.PROPS()).tell(new UserGenerateSecurityData.GenerateSecurityMsg(), self());
            getContext().actorOf(UserEligibleActor.PROPS(userRepository)).tell(u, self());
            if (organisationCreationRequired()) {
                Organisation organisation = new Organisation(u.email);
                getContext().actorSelection(EndpointActor.ACTOR_PATH).tell(new OrganisationCreatorActor.OrganisationCreateMsg(u.getRequester(), u.originalEvent(), organisation, false), self());

            } else {
                organisationId = u.entityId;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("User '{}' attached to organisation which already exist with id {}.", u.getUsername(), u.getEntityId());
                }
            }
        }
    }

    private boolean organisationCreationRequired() {
        return isBlank(message.entityId);
    }

    private void onSecurityDataGenerated(UserGenerateSecurityData.UserSecurityDataMsg msg) {
        password = msg.getPassword();
        keyPair = msg.getKeyPair();
        isReadyToStore();
    }

    private void onUserIsEligible(UserEligibleActor.UserEligibleResultMsg r) {
        isValid = r.isValid();
        if (isValid) {
            isReadyToStore();
        } else {
            originalActor.forward(r, getContext());
            getContext().stop(self());
        }
    }

    private void onOrganisationCreated(OrganisationCreatorActor.OrganisationCreatedResultMsg msg) {
        organisationId = msg.getOrganisationId();

        isReadyToStore();
    }

    private void isReadyToStore() {
        if (isValid && keyPair != null && StringUtils.isNotBlank(password) && StringUtils.isNotBlank(organisationId)) {
            String encodePublicKey = RSAUtils.encodePublicKey((RSAPublicKey) keyPair.getPublic(), message.email);
            User user = new User(message.id, organisationId, message.username, message.username, message.email, password, encodePublicKey, message.isRoot);
            boolean added = userRepository.addUser(user);
            getContext().actorSelection(EndpointActor.ACTOR_PATH).tell(new OrganisationMessage.ChangeUserToOrganisationMsg(null, OrganisationMessage.TypeChange.ADD, message.originalEvent(), message.id, organisationId, organisationCreationRequired()), self());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Adding user {} to organisation ", message.username, organisationId);
            }

            //getContext().actorSelection(EndpointActor.ACTOR_PATH).tell(new OrganisationMessage.ChangeUserToOrganisationMsg(message.getRequester(), OrganisationMessage.TypeChange.ADD, message.originalEvent(), message.id, organisationId, message.isRoot), self());
            if (added) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("User {} successfully created.", message.getUsername());
                }
                originalActor.tell(new UserCreateResultMsg(message.getRequester(), message.originalEvent(), user, keyPair), self());
                List<String> to = new ArrayList<>();
                to.add(message.getEmail());
                if (message.getRequester() != null) {
                    to.add(message.getRequester().getEmail());
                }
                // TODO: use velocity to use html template to create the content of Email.
                String content = getMailContent(user);
                Set<EmailSender.Attachment> attachments = new HashSet<>();

                String privateKeyContent = RSAUtils.encodedPrivateKey(keyPair.getPrivate());
                attachments.add(new EmailSender.PlainTextAttachment<>(privateKeyContent, user.getUsername() + ".key"));
                attachments.add(new EmailSender.PlainTextAttachment<>(user.getSshPublicKey(), user.getUsername() + ".pub"));
                EmailSenderActor.EmailSenderMsg emailSenderMsg = new EmailSenderActor.EmailSenderMsg(to, null, null, String.format("Kodo Kojo user %s created", user.getUsername()), content, true, attachments);
                getContext().actorFor(EndpointActor.ACTOR_PATH).tell(emailSenderMsg, self());

                getContext().stop(self());
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Unable to store user {}", user);
            }
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Not yet ready to store the user.");
        }
    }

    public static class EventUserCreateMsg extends EventUserRequestMessage {

        protected final String id;

        protected final String email;

        protected final String username;

        protected final String entityId;

        protected final boolean isRoot;

        private final boolean comeFromEventBus;

        public EventUserCreateMsg(User requester, Event request, String id, String email, String username, String entityId, boolean isRoot) {
            this(requester, request, id, email, username, entityId, isRoot, false);
        }

        public EventUserCreateMsg(User requester, Event request, String id, String email, String username, String entityId, boolean isRoot, boolean comeFromEventBus) {
            super(requester, request);
            this.isRoot = isRoot;
            if (isBlank(id)) {
                throw new IllegalArgumentException("id must be defined.");
            }
            if (isBlank(email)) {
                throw new IllegalArgumentException("email must be defined.");
            }

            if (isBlank(username)) {
                throw new IllegalArgumentException("username must be defined.");
            }
            this.id = id;
            this.email = email;
            this.username = username;
            this.entityId = entityId;
            this.comeFromEventBus = comeFromEventBus;
        }

        @Override
        public boolean initialSenderIsEventBus() {
            return comeFromEventBus;
        }

        public String getId() {
            return id;
        }

        public String getEmail() {
            return email;
        }

        public String getUsername() {
            return username;
        }

        public String getEntityId() {
            return entityId;
        }
    }

    public static class UserCreateResultMsg extends EventUserReplyMessage {

        private final User user;

        private final KeyPair keyPair;

        public UserCreateResultMsg(User requester, Event request, User user, KeyPair keyPair) {
            super(requester, request, Event.USER_CREATION_REPLY, new UserCreationReply(user.getIdentifier(), keyPair, user.getEmail(), false, true));
            if (keyPair == null) {
                throw new IllegalArgumentException("keyPair must be defined.");
            }
            this.user = user;
            this.keyPair = keyPair;
        }

        public User getUser() {
            return user;
        }

        public KeyPair getKeyPair() {
            return keyPair;
        }
    }

    public static class UserInWaitinglistResultMsg extends EventUserReplyMessage {

        private final UserInWaitingList userInWaitingList;

        public UserInWaitinglistResultMsg(User requester, Event request, UserInWaitingList userInWaitingList) {
            super(requester, request, Event.USER_CREATION_REPLY, new UserCreationReply(null, null, userInWaitingList.getEmail(), true, true));
            this.userInWaitingList = userInWaitingList;
        }

        public UserInWaitingList getUserInWaitingList() {
            return userInWaitingList;
        }
    }

    private static String getMailContent(User user) {
        return "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n" +
                "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
                "<head>\n" +
                "  <meta charset=\"utf-8\"> <!-- utf-8 works for most cases -->\n" +
                "  <meta name=\"viewport\" content=\"width=device-width\"> <!-- Forcing initial-scale shouldn't be necessary -->\n" +
                "  <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\"> <!-- Use the latest (edge) version of IE rendering engine -->\n" +
                "  <title></title> <!-- The title tag shows in email notifications, like Android 4.4. -->\n" +
                "\n" +
                "  <!-- Web Font / @font-face : BEGIN -->\n" +
                "  <!-- NOTE: If web fonts are not required, lines 9 - 26 can be safely removed. -->\n" +
                "\n" +
                "  <!-- Desktop Outlook chokes on web font references and defaults to Times New Roman, so we force a safe fallback font. -->\n" +
                "  <!--[if mso]>\n" +
                "  <style>\n" +
                "    * {\n" +
                "      font-family: sans-serif !important;\n" +
                "    }\n" +
                "  </style>\n" +
                "  <![endif]-->\n" +
                "\n" +
                "  <!-- All other clients get the webfont reference; some will render the font and others will silently fail to the fallbacks. More on that here: http://stylecampaign.com/blog/2015/02/webfont-support-in-email/ -->\n" +
                "  <!--[if !mso]><!-->\n" +
                "  <!-- insert web font reference, eg: <link href='https://fonts.googleapis.com/css?family=Roboto:400,700' rel='stylesheet' type='text/css'> -->\n" +
                "  <!--<![endif]-->\n" +
                "\n" +
                "  <!-- Web Font / @font-face : END -->\n" +
                "\n" +
                "  <!-- CSS Reset -->\n" +
                "  <style type=\"text/css\">\n" +
                "\n" +
                "    /* What it does: Remove spaces around the email design added by some email clients. */\n" +
                "    /* Beware: It can remove the padding / margin and add a background color to the compose a reply window. */\n" +
                "    html,\n" +
                "    body {\n" +
                "      margin: 0 !important;\n" +
                "      padding: 0 !important;\n" +
                "      height: 100% !important;\n" +
                "      width: 100% !important;\n" +
                "    }\n" +
                "\n" +
                "    /* What it does: Stops email clients resizing small text. */\n" +
                "    * {\n" +
                "      -ms-text-size-adjust: 100%;\n" +
                "      -webkit-text-size-adjust: 100%;\n" +
                "    }\n" +
                "\n" +
                "    /* What it does: Centers email on Android 4.4 */\n" +
                "    div[style*=\"margin: 16px 0\"] {\n" +
                "      margin: 0 !important;\n" +
                "    }\n" +
                "\n" +
                "    /* What it does: Stops Outlook from adding extra spacing to tables. */\n" +
                "    table,\n" +
                "    td {\n" +
                "      mso-table-lspace: 0pt !important;\n" +
                "      mso-table-rspace: 0pt !important;\n" +
                "    }\n" +
                "\n" +
                "    /* What it does: Fixes webkit padding issue. Fix for Yahoo mail table alignment bug. Applies table-layout to the first 2 tables then removes for anything nested deeper. */\n" +
                "    table {\n" +
                "      border-spacing: 0 !important;\n" +
                "      border-collapse: collapse !important;\n" +
                "      table-layout: fixed !important;\n" +
                "      Margin: 0 auto !important;\n" +
                "    }\n" +
                "\n" +
                "    table table table {\n" +
                "      table-layout: auto;\n" +
                "    }\n" +
                "\n" +
                "    /* What it does: Uses a better rendering method when resizing images in IE. */\n" +
                "    img {\n" +
                "      -ms-interpolation-mode: bicubic;\n" +
                "    }\n" +
                "\n" +
                "    /* What it does: Overrides styles added when Yahoo's auto-senses a link. */\n" +
                "    .yshortcuts a {\n" +
                "      border-bottom: none !important;\n" +
                "    }\n" +
                "\n" +
                "    /* What it does: A work-around for iOS meddling in triggered links. */\n" +
                "    .mobile-link--footer a,\n" +
                "    a[x-apple-data-detectors] {\n" +
                "      color: inherit !important;\n" +
                "      text-decoration: underline !important;\n" +
                "    }\n" +
                "  </style>\n" +
                "\n" +
                "  <!-- Progressive Enhancements -->\n" +
                "  <style>\n" +
                "\n" +
                "    /* What it does: Hover styles for buttons */\n" +
                "    .button-td,\n" +
                "    .button-a {\n" +
                "      transition: all 100ms ease-in;\n" +
                "    }\n" +
                "\n" +
                "    .button-td:hover,\n" +
                "    .button-a:hover {\n" +
                "      background: #555555 !important;\n" +
                "      border-color: #555555 !important;\n" +
                "    }\n" +
                "\n" +
                "    /* Media Queries */\n" +
                "    @media screen and (max-width: 480px) {\n" +
                "\n" +
                "      /* What it does: Forces elements to resize to the full width of their container. Useful for resizing images beyond their max-width. */\n" +
                "      .fluid,\n" +
                "      .fluid-centered {\n" +
                "        width: 100% !important;\n" +
                "        max-width: 100% !important;\n" +
                "        height: auto !important;\n" +
                "        margin-left: auto !important;\n" +
                "        margin-right: auto !important;\n" +
                "      }\n" +
                "\n" +
                "      /* And center justify these ones. */\n" +
                "      .fluid-centered {\n" +
                "        margin-left: auto !important;\n" +
                "        margin-right: auto !important;\n" +
                "      }\n" +
                "\n" +
                "      /* What it does: Forces table cells into full-width rows. */\n" +
                "      .stack-column,\n" +
                "      .stack-column-center {\n" +
                "        display: block !important;\n" +
                "        width: 100% !important;\n" +
                "        max-width: 100% !important;\n" +
                "        direction: ltr !important;\n" +
                "      }\n" +
                "\n" +
                "      /* And center justify these ones. */\n" +
                "      .stack-column-center {\n" +
                "        text-align: center !important;\n" +
                "      }\n" +
                "\n" +
                "      /* What it does: Generic utility class for centering. Useful for images, buttons, and nested tables. */\n" +
                "      .center-on-narrow {\n" +
                "        text-align: center !important;\n" +
                "        display: block !important;\n" +
                "        margin-left: auto !important;\n" +
                "        margin-right: auto !important;\n" +
                "        float: none !important;\n" +
                "      }\n" +
                "\n" +
                "      table.center-on-narrow {\n" +
                "        display: inline-block !important;\n" +
                "      }\n" +
                "\n" +
                "      a.link,\n" +
                "      a.link:hover,\n" +
                "      a.link:visited {\n" +
                "        font-family: sans-serif;\n" +
                "        font-size: 15px;\n" +
                "        mso-height-rule: exactly;\n" +
                "        line-height: 20px;\n" +
                "        color: #4A4A52;\n" +
                "        text-decoration: underline;\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "  </style>\n" +
                "\n" +
                "</head>\n" +
                "<body width=\"100%\" bgcolor=\"#FFF\" style=\"Margin: 0;\">\n" +
                "<table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" height=\"100%\" width=\"100%\" bgcolor=\"#4A4A52\" style=\"border-collapse:collapse;\">\n" +
                "  <tr>\n" +
                "    <td valign=\"top\">\n" +
                "  <tr>\n" +
                "    <td width=\"100%\">\n" +
                "\n" +
                "      <!-- Visually Hidden Preheader Text : BEGIN -->\n" +
                "      <div style=\"display:none;font-size:1px;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;mso-hide:all;font-family: sans-serif;\">\n" +
                "        Kodo Kojo\n" +
                "      </div>\n" +
                "      <!-- Visually Hidden Preheader Text : END -->\n" +
                "\n" +
                "      <div style=\"max-width: 680px; margin-left:auto; margin-right: auto;\">\n" +
                "        <!--[if (gte mso 9)|(IE)]>\n" +
                "        <table cellspacing=\"0\" cellpadding=\"0\" border=\"0\" align=\"center\">\n" +
                "          <tr>\n" +
                "            <td>\n" +
                "        <![endif]-->\n" +
                "\n" +
                "\n" +
                "        <!-- Email Body : BEGIN -->\n" +
                "        <table cellspacing=\"0\" cellpadding=\"0\" border=\"0\" align=\"center\" bgcolor=\"#FFF\" style=\"max-width: 680px; margin-left:auto; margin-right: auto;\">\n" +
                "\n" +
                "          <!-- Hero Image, Flush : BEGIN -->\n" +
                "          <tr>\n" +
                "            <td>\n" +
                "              <table cellspacing=\"0\" cellpadding=\"0\" border=\"0\" align=\"center\" bgcolor=\"#3A3A3D\" width=\"100%\" height=\"70px\" style=\"max-width: 680px;\">\n" +
                "                <tr>\n" +
                "                  <td style=\"height: 110px; background-color: #3A3A3D;\">\n" +
                "                    <img src=\"http://blog.xebia.fr/wp-content/uploads/2016/10/logo-white-kodokojo-baseline-simplified.png\" alt=\"Kodo Kojo logo\" style=\"border:none; margin-left: 20px; margin-top: 10px\" width=\"180\"/>\n" +
                "                    <div style=\"width: 400px; text-align: right; color: #fff; font-family:myriad pro, Arial, Helvetica, sans-serif; font-size: 18px; float:right; padding-right: 20px; padding-top: 20px\">\n" +
                "                      " + user.getUsername() + " account details\n" +
                "                    </div>\n" +
                "                  </td>\n" +
                "                </tr>\n" +
                "                <tr>\n" +
                "                  <td style=\"height: 10px; background-color: #60DADF\"></td>\n" +
                "                </tr>\n" +
                "              </table>\n" +
                "\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "\n" +
                "          <!-- CONTENT -->\n" +
                "          <tr>\n" +
                "            <td>\n" +
                "              <table cellspacing=\"0\" cellpadding=\"0\" border=\"0\" width=\"100%\" style=\"max-width: 680px;\">\n" +
                "                <tr>\n" +
                "                  <td style=\"padding: 15px 15px 0 15px; text-align: left; font-family: sans-serif; font-size: 15px; mso-height-rule: exactly; line-height: 20px; color: #4A4A52;\">\n" +
                "\n" +
                "                    <p style=\"word-wrap: break-word; color: #4A4A52;\"><b>Welcome to Kodo Kojo, we are very excited to have you on board!</b></p>\n" +
                "\n" +
                "                    <p style=\"word-wrap: break-word; color: #4A4A52;\">\n" +
                "                      Thanks for signing up to our SaaS service, we hope you will enjoy it!\n" +
                "                      Feel free to report any suggestion and problem to <a href=\"mailto:help@kodokojo.io\" target=\"_blank\" class=\"link\" style=\"font-family: sans-serif; font-size: 15px; mso-height-rule: exactly; line-height: 20px; color: #4A4A52; text-decoration: underline;\">help@kodokojo.io</a>.\n" +
                "                      You can also <a href=\"https://gitter.im/kodokojo/kodokojo\" target=\"_blank\" class=\"link\" style=\"font-family: sans-serif; font-size: 15px; mso-height-rule: exactly; line-height: 20px; color: #4A4A52; text-decoration: underline;\">chat with us directly on our gitter</a>.\n" +
                "                      For any additionnal information, please visit <a href=\"https://kodokojo.io\" target=\"_blank\" class=\"link\" style=\"font-family: sans-serif; font-size: 15px; mso-height-rule: exactly; line-height: 20px; color: #4A4A52; text-decoration: underline;\">kodokojo.io</a>.\n" +
                "                    </p>\n" +
                "\n" +
                "                    <p style=\"word-wrap: break-word; color: #4A4A52;\">\n" +
                "                      You can log in to <a href=\"https://my.kodokojo.io/login\" target=\"_blank\" class=\"link\" style=\"font-family: sans-serif; font-size: 15px; mso-height-rule: exactly; line-height: 20px; color: #4A4A52; text-decoration: underline;\"><b>my.kodokojo.io</b></a>.<br/>\n" +
                "                    </p>\n" +
                "\n" +
                "                    <p style=\"word-wrap: break-word; color: #4A4A52;\">\n" +
                "                      <b>User name:</b><br/>\n" +
                "                      " + user.getUsername() + "\n" +
                "                    </p>\n" +
                "\n" +
                "                    <p style=\"word-wrap: break-word; color: #4A4A52;\">\n" +
                "                      <b>Password:</b><br/>\n" +
                "                      " + user.getPassword() +
                "                    </p>\n" +
                "\n" +
                "                    <p style=\"word-wrap: break-word; color: #4A4A52;\">\n" +
                "                      <b>Auto generated SSH key:</b><br/>\n" +
                "                      It is attached to this email in two separate files.<br/>\n" +
                "                      You can replace it by your own via <a href=\"https://my.kodokojo.io/members\" target=\"_blank\" class=\"link\" style=\"font-family: sans-serif; font-size: 15px; mso-height-rule: exactly; line-height: 20px; color: #4A4A52; text-decoration: underline;\">the members page</a>.\n" +
                "                    </p>\n" +
                "\n" +
                "                    <p style=\"word-wrap: break-word; color: #4A4A52;\">\n" +
                "                      You can now continue building great things on your brand new software factory!<br/>\n" +
                "                      Cheers,<br/>\n" +
                "                      <b>--</b><br/>\n" +
                "                      <b>The Kodo Kojo Team</b>\n" +
                "\n" +
                "                    </p>\n" +
                "                  </td>\n" +
                "                </tr>\n" +
                "              </table>\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "        </table>\n" +
                "        <!-- FOOTER -->\n" +
                "\n" +
                "        <table width=\"100%\" style=\"max-width: 680px; margin: 0; border: 0;\" cellpadding=\"0px\" cellspacing=\"0px\">\n" +
                "          <tr>\n" +
                "            <td width=\"100%\" bgcolor=\"#FFF\">\n" +
                "              <div class=\"kodo-kojo-logo\" style=\"text-align:center; padding-bottom: 20px\">\n" +
                "                <a href=\"https://kodokojo.io/\" target=\"_blank\"><img src=\"http://blog.xebia.fr/wp-content/uploads/2016/10/logo-black-kodokojo-baseline-simplified.png\" alt=\"Kodo Kojo logo\" style=\"border:none;\" width=\"180\"/></a>\n" +
                "              </div>\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "          <tr>\n" +
                "            <td width=\"100%\" bgcolor=\"#60DADF\" style=\"height: 10px; background-color: #60DADF\"></td>\n" +
                "          </tr>\n" +
                "          <tr>\n" +
                "            <td width=\"100%\" bgcolor=\"#3A3A3D\" align=\"center\" style=\"color: #ffffff; height:60px; background-color: #3A3A3D;\">\n" +
                "              <a style=\"color:#dadae5; text-align:center; font-family:myriad pro, Arial, Helvetica, sans-serif; font-size:16px; text-decoration:none; \" target=\"_blank\" href=\"https://kodokojo.io\"> &gt;| kodokojo.io |&lt; </a>\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "        </table>\n" +
                "      </div>\n" +
                "    </td>\n" +
                "  </tr>\n" +
                "</table>\n" +
                "</body>\n" +
                "</html>\n";
    }

}
