package org.ub.utilbot.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.ub.utilbot.commandutils.Command;
import org.ub.utilbot.commandutils.CommandContext;
import org.ub.utilbot.commandutils.MeetUtils;
import org.ub.utilbot.entities.Meeting;
import org.ub.utilbot.entities.Professor;
import org.ub.utilbot.entities.User;
import org.ub.utilbot.entities.UserToMeeting;
import org.ub.utilbot.repositories.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class RemindCommand implements Command, ApplicationContextAware {

    String[] days = {"Sunday","Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

    private final Logger log = LogManager.getLogger(RemindCommand.class);

    private ApplicationContext appContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserToMeetingRepository utoRepository;

    @Override
    public String getName() {
        return "remind";
    }

    @Override
    public String getDescription() {
        return "Get reminded for a lecture or tutoring. You will receive a message 30 minutes before it starts";
    }

    @Override
    public String getUsage() {
        return "!remind [subject] [identifier]";
    }

    @Override
    public void onCommand(CommandContext context) {
        MeetUtils util = appContext.getBean(MeetUtils.class);

        // Checks if there are arguments present
        if (context.getArgs().length == 0) {
            context.getChannel().sendMessage("I need arguments to figure out which meetings you want information for.\nThe usage is as follows: `!meeting [subject] [identifier]`").queue();
            return;
        }

        // Checks if the first argument is a valid subject
        List<String> types = util.getTypes();
        if (!types.contains(context.getArgs()[0])) {
            context.getChannel().sendMessage("Please give me the subject you want me to get the lectures for first. It should be one of these: " + types.toString()).queue();
            return;
        }

        // Subject is the first argument and the rest is concatenated to be the identifier
        String subject = context.getArgs()[0];

        String identifier = String.join(" ", Arrays.copyOfRange(context.getArgs(),1,context.getArgs().length));

        int day = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1) % 7;
        //new MeetUtils(profRepository,meetRepository,tutRepository);
        List<Meeting> meetings = util.getMeetings(subject,identifier,day);

        if (meetings.size() == 0) {

            String response = "I could not find any fitting meetings for your response. Maybe try using group numbers for tutorings or just the subject alone for lectures.";
            context.getChannel().sendMessage(response).queue();
        }
        else if (meetings.size() == 1) {
            Meeting m = meetings.get(0);
            // Checks if the user is already in the database
            List<User> users = ((List<User>) userRepository.findAll()).stream()
                    .filter(u -> u.getDiscordId().equals(context.getMember().getId()))
                    .collect(Collectors.toList());

            if (users.size() == 0) {
                User u = new User();
                u.setDiscordId(context.getMember().getId());

                u = userRepository.save(u);
                users.add(u);
                log.info("Added User to database: " + u.toString());
            }
            User user = users.get(0);

            // Add UserToMeeting mapping to database
            UserToMeeting uto = new UserToMeeting();
            uto.setRefMeetingId(m.getId());
            uto.setRefUserId(user.getId());

            uto = utoRepository.save(uto);
            log.info("Added UserToMeeting instance: " + uto.toString());


            String response = "I signed you up to be reminded for " + subject + " at " + m.getStartTime() + " on " + days[m.getWeekday()] + ".\nTo remove this reminder, please run the command `!remove " + uto.getId() + "`";
            context.getChannel().sendMessage(response).queue();
        }
        // If all meetings are lectures are lectures
        else if (meetings.stream().allMatch(m -> m.getRefTutorId() == null)) {

            // Checks if the user is already in the database
            List<User> users = ((List<User>) userRepository.findAll()).stream()
                    .filter(u -> u.getDiscordId().equals(context.getMember().getId()))
                    .collect(Collectors.toList());

            if (users.size() == 0) {
                User u = new User();
                u.setDiscordId(context.getMember().getId());

                u = userRepository.save(u);
                users.add(u);
                log.info("Added User to database: " + u.toString());
            }
            User user = users.get(0);
            for (Meeting meet: meetings) {
                UserToMeeting uto = new UserToMeeting();
                uto.setRefMeetingId(meet.getId());
                uto.setRefUserId(user.getId());

                uto = utoRepository.save(uto);
                log.info("Added UserToMeeting instance: " + uto.toString());
                String response = "I signed you up to be reminded for " + subject + " at " + meet.getStartTime() + " on " + days[meet.getWeekday()] + ".\nTo remove this reminder, please run the command `!remove " + uto.getId() + "`";
                context.getChannel().sendMessage(response).queue();
            }
        }
        // It's not exact enough. Return the list and instructions on how to make it more exact
        else {
            String response = "The request isn't exact enough. There are several meetings that could be meant. Please use the group number as an identifier for a tutoring or leave the identifier blank to sign up for the lecture.\n" + util.meetResponse(meetings);
            context.getChannel().sendMessage(response).queue();
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.appContext = applicationContext;
    }
}
