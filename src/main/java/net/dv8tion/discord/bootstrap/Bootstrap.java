package net.dv8tion.discord.bootstrap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Bootstrap
{

    public static final String RECOMMENDED_BUILD_ROOT = "http://ci.pc-logix.com/job/Yuri/Promoted%20Build/artifact/build/libs/";
    public static final String BETA_BUILD_ROOT = "http://ci.pc-logix.com/job/Yuri//lastCompletedBuild/artifact/build/libs/";
    public static final String URL_REGEX = "\\<a href=\"Yuri-withDependencies-[0-9]*\\.[0-9]*\\.[0-9]*_[0-9]*\\.jar\">(Yui-withDependencies-[0-9]*\\.[0-9]*\\.[0-9]*_[0-9]*\\.jar)\\<\\/a\\>";

    public static final String VERSION = "1.3.1";
    public static final File BOT_JAR_FILE = new File("./Yuri.jar");
    public static final File BOT_JAR_FILE_OLD = new File("OLD_" + BOT_JAR_FILE.getName());

    // --  Yui specific exit codes --
    //Non error, no action exit codes.
    public static final int NORMAL_SHUTDOWN = 10;
    public static final int RESTART_EXITCODE = 11;
    public static final int NEWLY_CREATED_CONFIG = 12;

    //Non error, action required exit codes.
    public static final int UPDATE_LATEST_EXITCODE = 20;
    public static final int UPDATE_RECOMMENDED_EXITCODE = 21;

    //error exit codes.
    public static final int UNABLE_TO_CONNECT_TO_DISCORD = 30;
    public static final int BAD_USERNAME_PASS_COMBO = 31;
    public static final int NO_USERNAME_PASS_COMBO = 32;

    // -- Bootloader specific exit codes --
    public static final int UNKNOWN_EXITCODE = 50;
    public static final int DOWNLOAD_FAILED = 51;
    public static final int UPDATED_FAILED = 52;

    public static String[] START_BOT_COMMAND = new String[] {
        "java", "-Djava.io.tmpfile=%s", "-Dfile.encoding=UTF-8", "-jar", BOT_JAR_FILE.getPath()
    };

    enum UpdateStatus
    {
        SUCCESSFUL("successful"), FAILED("failed"), NONE("");

        private String commandArg;
        UpdateStatus(String commandArg)
        {
            this.commandArg = commandArg;
        }

        @Override
        public String toString()
        {
            return commandArg;
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException
    {        
        if (!BOT_JAR_FILE.exists())
        {
            if (!downloadBot(false))
            {
                System.out.println("Could not download Bot. Check Internet Connection and File System.");
                System.exit(DOWNLOAD_FAILED);
            }
        }

        System.out.println("Starting the Bootstrap Launch loop");
        UpdateStatus updateStatus = UpdateStatus.NONE;
        while (true)
        {
            ProcessBuilder builder = new ProcessBuilder();
            builder.environment().put("BootstrapVersion", VERSION);
            builder.inheritIO();
            START_BOT_COMMAND[1] = "-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir");
            builder.command(START_BOT_COMMAND);
            if (!updateStatus.equals(UpdateStatus.NONE))
            {
                builder.command().add(updateStatus.toString());
                updateStatus = UpdateStatus.NONE;
            }

            Process botProcess = builder.start();
            botProcess.waitFor();
            switch(botProcess.exitValue())
            {
                case NORMAL_SHUTDOWN:
                    System.out.println("The Bot requested to shutdown and not relaunch.\nShutting down...");
                    System.exit(0);
                    break;
                case RESTART_EXITCODE:
                    System.out.println("Bot stopped due to restart request. Restarting...");
                    break;
                case UPDATE_LATEST_EXITCODE:
                case UPDATE_RECOMMENDED_EXITCODE:
                    updateStatus = updateBot(botProcess.exitValue()) ? UpdateStatus.SUCCESSFUL : UpdateStatus.FAILED;
                    break;
                case NEWLY_CREATED_CONFIG:
                    //TODO: More to work on.
                    System.out.println("The config was created for the first time. Please input Email and Password values.");
                    System.exit(NEWLY_CREATED_CONFIG);
                    break;
                case UNABLE_TO_CONNECT_TO_DISCORD:
                    System.exit(UNABLE_TO_CONNECT_TO_DISCORD);
                    break;
                case BAD_USERNAME_PASS_COMBO:
                    System.exit(BAD_USERNAME_PASS_COMBO);
                    break;
                case NO_USERNAME_PASS_COMBO:
                    System.exit(NO_USERNAME_PASS_COMBO);
                    break;
                default:
                    System.out.println("The Bot's Exit code was unrecognized. ExitCode: " + botProcess.exitValue());
                    System.out.println("Shutting down now.");
                    System.exit(UNKNOWN_EXITCODE);
            }
        }
    }

    private static boolean updateBot(int updateCode)
    {
        try
        {
            Files.move(
                    BOT_JAR_FILE.toPath(),
                    BOT_JAR_FILE_OLD.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            if (!downloadBot(updateCode == UPDATE_LATEST_EXITCODE))
            {
                Files.move(
                        BOT_JAR_FILE_OLD.toPath(),
                        BOT_JAR_FILE.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Encountered an error while downloading the updated Bot. Reverting to old version.");
                return false;
            }
            System.out.println("Update Successful!");
            return true;
        }
        catch (IOException e)
        {
            System.out.println("Encountered problem when trying to move the Bot jar file.");
            e.printStackTrace();
            return false;
        }
    }

    private static boolean downloadBot(boolean useBetaBuilds)
    {
        String downloadUrl = useBetaBuilds ? getLatestBetaUrl() : getLatestRecommendedUrl();
        for (int i = 0; i < 3 && !BOT_JAR_FILE.exists(); i++)
        {
            try
            {
                if (i == 0)
                {
                    System.out.println("Attempting to download the Bot, Please wait...");
                    Downloader.file(downloadUrl, BOT_JAR_FILE.getPath());
                }
                else
                {
                    System.out.println("Failed to download the Bot, Will wait 5 second and try again.");
                    Thread.sleep(5000);
                    System.out.printf("Attempting to download bot, Attempt #%d, Please wait...\n", (i + 1));
                    Downloader.file(downloadUrl, BOT_JAR_FILE.getPath());
                }
                if (BOT_JAR_FILE.exists())
                {
                    System.out.println("Successfully downloaded the Bot!");
                    return true;
                } 
            }
            catch (IOException | InterruptedException e)
            {
                System.out.println("Encountered an IOError attempting to download the Bot.");
                e.printStackTrace();
            }
        }
        return false;
    }

    public static String getLatestRecommendedUrl()
    {
        String page = Downloader.webpage(RECOMMENDED_BUILD_ROOT);
        Pattern urlPattern = Pattern.compile(URL_REGEX);
        Matcher urlMatcher = urlPattern.matcher(page);
        if (urlMatcher.find())
        {
            return RECOMMENDED_BUILD_ROOT + urlMatcher.group(1);
        }
        else
            throw new RuntimeException("Could not find Recommended URL.");
    }

    public static String getLatestBetaUrl()
    {
        String page = Downloader.webpage(BETA_BUILD_ROOT);
        Pattern urlPattern = Pattern.compile(URL_REGEX);
        Matcher urlMatcher = urlPattern.matcher(page);
        if (urlMatcher.find())
        {
            return BETA_BUILD_ROOT + urlMatcher.group(1);
        }
        else
            throw new RuntimeException("Could not find Beta URL.");
    }
}
