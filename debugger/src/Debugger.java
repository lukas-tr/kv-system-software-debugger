import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.request.*;
import com.sun.jdi.event.*;

public class Debugger {
    private VirtualMachine vm;
    private Process proc;
    private EventRequestManager reqManager;
    private EventQueue q;

    private Location currentLocation;

    public static final int STEP_OVER = StepRequest.STEP_OVER;
    public static final int STEP_INTO = StepRequest.STEP_INTO;
    public static final int STEP_OUT = StepRequest.STEP_OUT;

    private static final int SHOW_LINES_AROUND_CURRENT_LOCATION = 10;

    public void launch(String className) {
        LaunchingConnector con = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> args = con.defaultArguments();
        args.get("main").setValue(className);
        try {
            vm = con.launch(args);
            proc = vm.process();
            reqManager = vm.eventRequestManager();
            q = vm.eventQueue();

            new Redirection(proc.getErrorStream(), System.err).start();
            new Redirection(proc.getInputStream(), System.out).start();

            waitForMain(className);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void waitForMain(String className) {
        MethodEntryRequest req = reqManager.createMethodEntryRequest();
        req.addClassFilter(className);
        req.enable();
        while (true) {
            try {
                EventSet events = q.remove();
                for (Event e : events)
                    if (e instanceof MethodEntryEvent me) {
                        req.disable();
                        currentLocation = me.location();
                        return;
                    } else if (e instanceof VMStartEvent) {
                        System.out.println("VM started");
                        vm.resume();
                    } else if (e instanceof VMDisconnectEvent) {
                        System.out.println("VM disconnected");
                        return;
                    } else {
                        System.out.println("Event: " + e);
                        vm.resume();
                    }
            } catch (VMDisconnectedException e) {
                System.out.println("VM disconnected");
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void printFileOfCurrentLocation() {
        if (currentLocation == null) {
            System.out.println("No current location");
            return;
        }
        try {
            printSource(currentLocation);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void continueExecution() {
        vm.resume();
        while (true) {
            try {
                EventSet events = q.remove();
                for (Event e : events) {
                    System.out.println("Event: " + e);
                    if (e instanceof StepEvent se) {
                        currentLocation = se.location();
                        System.out.println("Current position: " + se.location().method().name() + " at line "
                                + se.location().lineNumber());
                        printVars(se.thread().frame(0));
                        reqManager.deleteEventRequest(se.request());
                        return;
                    } else if (e instanceof BreakpointEvent be) {
                        currentLocation = be.location();
                        System.out.println("Hit breakpoint: " + be.location().lineNumber() + " in "
                                + be.location().method().toString());
                        printVars(be.thread().frame(0));
                        return;
                    }
                }
            } catch (VMDisconnectedException e) {
                System.out.println("VM disconnected");
                currentLocation = null;
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void printStackTrace() throws IncompatibleThreadStateException {
        List<StackFrame> frames = vm.allThreads().get(0).frames();
        for (StackFrame frame : frames) {
            System.out.println(frame.location().method().toString() + " at line " + frame.location().lineNumber());
        }
    }

    private List<BreakpointRequest> getBreakpoints() {
        return reqManager.breakpointRequests().stream().filter(b -> b.isEnabled()).toList();
    }

    public void listBreakpoints() {
        int i = 0;
        for (BreakpointRequest breakpoint : getBreakpoints()) {
            if (breakpoint.isEnabled()) {
                i++;
                System.out.println(i + ": " + breakpoint.location().toString());
            }
        }
    }

    public void removeBreakpoint(int breakpointId) {
        List<BreakpointRequest> breakpoints = getBreakpoints();
        if (breakpointId < 1 || breakpointId > breakpoints.size()) {
            throw new IllegalArgumentException("Breakpoint id out of range");
        }
        BreakpointRequest breakpoint = breakpoints.get(breakpointId - 1);
        breakpoint.disable();
    }

    public void addBreakpoint(int lineNumber) {
        try {
            List<Location> locs = currentLocation.method().locationsOfLine(lineNumber);
            if (locs.size() > 0) {
                Location loc = locs.get(0);
                BreakpointRequest req = reqManager.createBreakpointRequest(loc);
                req.enable();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void printLocals() {
        try {
            printVars(vm.allThreads().get(0).frame(0));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void step(int stepType) {
        try {
            StepRequest req = reqManager.createStepRequest(vm.allThreads().get(0), StepRequest.STEP_LINE, stepType);
            req.addCountFilter(1);
            req.enable();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void printSource(Location methodLocation) throws AbsentInformationException {
        String sourcePath = methodLocation.sourcePath();
        int lineNumber = methodLocation.lineNumber();

        List<String> lines = getSourceFromLocalDirectory(sourcePath);
        if (lines == null) {
            lines = getSourceFromJdk(methodLocation);
        }
        if (lines == null) {
            System.out.println("Source file not found: " + sourcePath);
            return;
        }

        List<BreakpointRequest> breakpoints = getBreakpoints();
        // print all lines with line numbers
        int start = Math.max(0, lineNumber - SHOW_LINES_AROUND_CURRENT_LOCATION);
        int end = Math.min(lines.size(), lineNumber + SHOW_LINES_AROUND_CURRENT_LOCATION);
        for (int i = start; i < end; i++) {
            final int index = i;
            if (i == lineNumber - 1) {
                System.out.println("\033[0;31m" + (i + 1) + ": " + lines.get(i));
            } else if (breakpoints.stream().anyMatch(b -> b.location().lineNumber() == index + 1)) {
                System.out.println("\033[0;31m*\033[0m: " + lines.get(i));
            } else {
                System.out.println("\033[0m" + (i + 1) + ": " + lines.get(i));
            }
        }
    }

    private static List<String> getSourceFromLocalDirectory(String sourcePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(sourcePath))) {
            return reader.lines().toList();
        } catch (FileNotFoundException ex) {
            System.out.println("Source file not found in local directory: " + sourcePath);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    private static List<String> getSourceFromJdk(Location methodLocation) throws AbsentInformationException {
        String home = System.getProperty("java.home");
        String sourcePath = methodLocation.sourcePath();
        String path = home + "/lib/src.zip";
        try (ZipFile zip = new ZipFile(path)) {
            Optional<? extends ZipEntry> entry = zip.stream().filter(e -> e.getName().contains(sourcePath))
                    .sorted((a, b) -> a.getName().length() - b.getName().length()).findFirst();
            if (entry.isEmpty()) {
                System.out.println("Source file not found in " + path + " (this was only tested with jdk 17.0.9-ms)");
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry.get())))) {
                return reader.lines().toList();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    static void printVars(StackFrame frame) {
        try {
            for (LocalVariable v : frame.visibleVariables()) {
                System.out.print(v.type().name() + " " + v.name() + " = ");
                printValue(frame.getValue(v));
                System.out.println();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void printValue(Value value) {
        if (value instanceof IntegerValue val) {
            System.out.print(val.value() + " ");
        } else if (value instanceof StringReference val) {
            System.out.print("\"" + val.value() + "\" ");
        } else if (value instanceof ArrayReference val) {
            System.out.print("[");
            for (Value v : val.getValues()) {
                printValue(v);
            }
            System.out.print("]");
        } else if (value instanceof ObjectReference val) {
            System.out.print("{ ");
            for (Field f : val.referenceType().allFields()) {
                System.out.print(f.name() + " = ");
                printValue(val.getValue(f));
                System.out.print(", ");
            }
            System.out.print("}");
        } else {
            System.out.print(value + " ");
        }
    }
}
