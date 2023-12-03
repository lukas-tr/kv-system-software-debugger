import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.sun.jdi.request.StepRequest;

public class App {
    public static void main(String[] args) throws Exception {
        App app = new App();
        app.run();
    }

    Debugger debugger;

    public App() {
        debugger = new Debugger();
    }

    public void run() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String userInput;
        String[] command;

        printHelp();

        while (true) {
            System.out.print("\033[0;32m > ");
            userInput = reader.readLine();
            System.out.print("\033[0m");
            if (userInput == null) {
                break;
            }
            command = userInput.split(" ");

            try {
                if (command.length == 0) {
                    continue;
                } else if (command[0].equals("help")) {
                    printHelp();
                } else if (command[0].equals("exit")) {
                    break; // TODO: exit
                } else if (command[0].equals("launch")) {
                    String className = requireArg(command, 1, "class name");
                    debugger.launch(className);
                    debugger.printFileOfCurrentLocation();
                } else if (command[0].equals("continue")) {
                    debugger.continueExecution();
                    debugger.printFileOfCurrentLocation();
                } else if (command[0].equals("over")) {
                    debugger.step(StepRequest.STEP_OVER);
                    debugger.continueExecution();
                    debugger.printFileOfCurrentLocation();
                } else if (command[0].equals("into")) {
                    debugger.step(StepRequest.STEP_INTO);
                    debugger.continueExecution();
                    debugger.printFileOfCurrentLocation();
                } else if (command[0].equals("out")) {
                    debugger.step(StepRequest.STEP_OUT);
                    debugger.continueExecution();
                    debugger.printFileOfCurrentLocation();
                } else if (command[0].equals("break")) {
                    int lineNumber = requireNumber(command, 1, "line");
                    debugger.addBreakpoint(lineNumber);
                    debugger.printFileOfCurrentLocation();
                    System.out.println("Breakpoints: ");
                    debugger.listBreakpoints();
                } else if (command[0].equals("trace")) {
                    debugger.printStackTrace();
                } else if (command[0].equals("breakpoints")) {
                    debugger.listBreakpoints();
                } else if (command[0].equals("remove")) {
                    int breakpointId = requireNumber(command, 1, "breakpoint id");
                    debugger.removeBreakpoint(breakpointId);
                    debugger.printFileOfCurrentLocation();
                    System.out.println("Breakpoints: ");
                    debugger.listBreakpoints();
                } else if (command[0].equals("locals")) {
                    debugger.printLocals();
                } else {
                    System.out.println("\033[0;31m" + "Unknown command: " + command[0]);
                }
            } catch (IllegalArgumentException e) {
                System.out.println("\033[0;31m" + e.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void printHelp() {
        System.out.println("\033[0;34m Commands:");
        System.out.println("help - print this help");
        System.out.println("exit - exit application");
        System.out.println("launch <class name> - connect to debuggee");
        System.out.println("continue - continue execution");
        System.out.println("over - step over");
        System.out.println("into - step into");
        System.out.println("out - step out");
        System.out.println("break <line> - add breakpoint");
        System.out.println("breakpoints - list breakpoints");
        System.out.println("remove <breakpoint id> - remove breakpoint");
        System.out.println("trace - print stack trace");
        System.out.println("locals - print local variables");
    }

    private String requireArg(String[] command, int index, String name) {
        if (command.length <= index) {
            throw new IllegalArgumentException("Missing argument: <" + name + ">");
        }
        return command[index];
    }

    private int requireNumber(String[] command, int index, String name) {
        String arg = requireArg(command, index, name);
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Argument <" + name + "> requires a number");
        }
    }
}
