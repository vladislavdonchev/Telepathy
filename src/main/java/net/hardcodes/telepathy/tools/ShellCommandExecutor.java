package net.hardcodes.telepathy.tools;

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class ShellCommandExecutor {

    private static final String LOG_TAG = ShellCommandExecutor.class.getName();

    private final static int NON_TRACKED_COMMAND_CODE = -1;

    public static final String ACTION_SHELL_COMMAND_EXECUTED = "shellCommandExecuted";
    public static final String KEY_COMMAND = "command";
    public static final String KEY_COMMAND_RESULT = "commandOutput";

    public static ShellCommandExecutor instance;
    private static Shell.Interactive rootSession;

    private ShellCommandExecutor() {
    }

    public static ShellCommandExecutor getInstance() {
        if (instance == null) {
            instance = new ShellCommandExecutor();
        }
        return instance;
    }

    public static boolean isSUAvailable() {
        return Shell.SU.available();
    }

    public static class ShellCommandResult implements Parcelable {

        private int commandCode;
        private int exitCode;
        private List<String> output;

        public ShellCommandResult(int commandCode, int exitCode, List<String> output) {
            this.commandCode = commandCode;
            this.exitCode = exitCode;
            this.output = output;
        }

        public int getCommandCode() {
            return commandCode;
        }

        public void setCommandCode(int commandCode) {
            this.commandCode = commandCode;
        }

        public int getExitCode() {
            return exitCode;
        }

        public void setExitCode(int exitCode) {
            this.exitCode = exitCode;
        }

        public List<String> getOutput() {
            return output;
        }

        public void setOutput(List<String> output) {
            this.output = output;
        }

        protected ShellCommandResult(Parcel in) {
            commandCode = in.readInt();
            exitCode = in.readInt();
            if (in.readByte() == 0x01) {
                output = new ArrayList<String>();
                in.readList(output, String.class.getClassLoader());
            } else {
                output = null;
            }
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(commandCode);
            dest.writeInt(exitCode);
            if (output == null) {
                dest.writeByte((byte) (0x00));
            } else {
                dest.writeByte((byte) (0x01));
                dest.writeList(output);
            }
        }

        public static final Creator<ShellCommandResult> CREATOR = new Creator<ShellCommandResult>() {
            @Override
            public ShellCommandResult createFromParcel(Parcel in) {
                return new ShellCommandResult(in);
            }

            @Override
            public ShellCommandResult[] newArray(int size) {
                return new ShellCommandResult[size];
            }
        };
    }

    public void runCommand(final String command) {
        runCommand(command, NON_TRACKED_COMMAND_CODE, null, true);
    }

    public void runCommand(final String[] command) {
        runCommand(command, NON_TRACKED_COMMAND_CODE, null, true);
    }

    public void runCommand(final String command, int commandCode, Shell.OnCommandResultListener commandResultListener) {
        runCommand(command, commandCode, commandResultListener, false);
    }

    public void runCommand(final String[] command, int commandCode, Shell.OnCommandResultListener commandResultListener) {
        runCommand(command, commandCode, commandResultListener, false);
    }

    public void runCommand(final String command, int commandCode, Shell.OnCommandResultListener commandResultListener, final boolean discardOutput) {
        runCommand(new String[]{command}, commandCode, commandResultListener, discardOutput);
    }

    public void runCommand(final String[] command, int commandCode, Shell.OnCommandResultListener commandResultListener, final boolean discardOutput) {
        if (rootSession == null) {
            openShell();
        }

        Log.d("SHELL", " CODE: " + commandCode + " LISTENER: " + commandResultListener);

        if (commandResultListener == null) {
            commandResultListener = new Shell.OnCommandResultListener() {
                public void onCommandResult(int commandCode, int exitCode, List<String> output) {
                    if (!discardOutput) {
                        ShellCommandResult shellCommandResult = new ShellCommandResult(exitCode, commandCode, output);
                        broadcastCommandResult(command.toString(), shellCommandResult);
                    }
                }
            };
        }

        if (rootSession != null) {
            rootSession.addCommand(command, commandCode, commandResultListener);
        }
    }

    private void openShell() {
        rootSession = new Shell.Builder().
                useSU().
                setWantSTDERR(false).
                setWatchdogTimeout(20).
                setMinimalLogging(false).open();
    }

    public void closeShell() {
        if (rootSession != null) {
            rootSession.close();
            rootSession = null;
        }
    }

    private void broadcastCommandResult(String command, ShellCommandResult shellCommandResult) {
        Intent shellCommandResultIntent = new Intent(ACTION_SHELL_COMMAND_EXECUTED);
        shellCommandResultIntent.putExtra(KEY_COMMAND, command);
        shellCommandResultIntent.putExtra(KEY_COMMAND_RESULT, shellCommandResult);
    }
}