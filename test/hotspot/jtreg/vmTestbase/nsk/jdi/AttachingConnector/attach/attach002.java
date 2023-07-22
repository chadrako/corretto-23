/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package nsk.jdi.AttachingConnector.attach;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.connect.*;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.ReferenceType;

import java.io.*;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;


/**
 * The test checks that debugger may establish connection with
 * a target VM via <code>com.sun.jdi.SharedMemoryAttach</code> connector.<br>
 * The test also analyzes exit code of debugee's process.
 */
public class attach002 extends Log {
    static final int PASSED = 0;
    static final int FAILED = 2;
    static final int JCK_STATUS_BASE = 95;
    static final String DEBUGEE_CLASS =
        "nsk.jdi.AttachingConnector.attach.attach002t";

    private Log log;

    private VirtualMachine vm;

    private int attempts;             // attempts to connect to the debugee VM
    private int delay = 4000;         // delay between connection attempts

    IORedirector outRedirector;
    IORedirector errRedirector;

    public static void main (String argv[]) {
        System.exit(run(argv,System.out) + JCK_STATUS_BASE);
    }

    public static int run(String argv[], PrintStream out) {
        return new attach002().runIt(argv, out);
    }

    private int runIt(String argv[], PrintStream out) {
        String name;
        Process proc;
        ArgumentHandler argHandler = new ArgumentHandler(argv);

// pass if "com.sun.jdi.SharedMemoryAttach" is not implemented
// on this platform
        if (argHandler.shouldPass("com.sun.jdi.SharedMemoryAttach"))
            return PASSED;

        log = new Log(out, argHandler);

        String args[] = argHandler.getArguments();

        // treat second positional argument as delay between connection attempts
        try {
            delay = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            log.complain("Incorrect test parameter: timeout value must be an integer");
            return FAILED;
        } catch (ArrayIndexOutOfBoundsException e) {
            // ignore: default delay will be used if no second argument present
        }

        // calculate number of connection attempts to not exceed WAITTIME
        long timeout = argHandler.getWaitTime() * 60 * 1000;
        attempts = (int)(timeout / delay);

        name = argHandler.getTransportSharedName();

        String java = argHandler.getLaunchExecPath()
                          + " " + argHandler.getLaunchOptions();
        String cmd = java +
            " -Xrunjdwp:transport=dt_shmem,server=y,address=" +
            name + " " + DEBUGEE_CLASS;

        Binder binder = new Binder(argHandler, log);
        log.display("command: " + cmd);
        Debugee debugee = binder.startLocalDebugee(cmd);
        debugee.redirectOutput(log);

        if ((vm = attachTarget(name)) == null) {
            log.complain("TEST: Unable to attach the debugee VM");
            debugee.close();
            return FAILED;
        }

        log.display("target VM: name=" + vm.name() + " JRE version=" +
            vm.version() + "\n\tdescription=" + vm.description());

        debugee.setupVM(vm);
        debugee.waitForVMInit(timeout);

        log.display("\nResuming debugee VM");
        debugee.resume();

        log.display("\nWaiting for debugee VM exit");
        int code = debugee.waitFor();
        if (code != (JCK_STATUS_BASE+PASSED)) {
            log.complain("Debugee VM has crashed: exit code=" + code);
            return FAILED;
        }
        log.display("Debugee VM: exit code=" + code);
        return PASSED;
    }

    private VirtualMachine attachTarget(String name) {
        Connector.Argument arg;

        if (name == null) {
            log.complain("TEST: shared memory name is required!");
            return null;
        }

        AttachingConnector connector =
            (AttachingConnector) findConnector("com.sun.jdi.SharedMemoryAttach");

        Map<java.lang.String,? extends com.sun.jdi.connect.Connector.Argument> cArgs = connector.defaultArguments();
        Iterator cArgsValIter = cArgs.keySet().iterator();
        while (cArgsValIter.hasNext()) {
            String argKey = (String) cArgsValIter.next();
            String argVal = null;

            if ((arg = (Connector.Argument) cArgs.get(argKey)) == null) {
                log.complain("Argument " + argKey.toString() +
                    "is not defined for the connector: " + connector.name());
            }
            if (arg.name().equals("name"))
                arg.setValue(name);

            log.display("\targument name=" + arg.name());
            if ((argVal = arg.value()) != null)
                log.display("\t\tvalue="+argVal);
            else log.display("\t\tvalue=NULL");
        }

        // make several attemts to connect to the debugee VM until WAITTIME exceeds
        for (int i = 0; i < attempts; i++) {
            try {
                return connector.attach(cArgs);
            } catch (IOException e) {
                // could not connect; sleep a few and make new attempt
                log.display("Connection attempt #" + i + " failed: " + e);
                try {
                    Thread.currentThread().sleep(delay);
                } catch (InterruptedException ie) {
                    log.complain("TEST INCOMPLETE: interrupted sleep: " + ie);
                }
            } catch (IllegalConnectorArgumentsException e) {
                log.complain("TEST: Illegal connector arguments: " +
                    e.getMessage());
                return null;
            } catch (Exception e) {
                log.complain("TEST: Internal error: " + e.getMessage());
                return null;
            }
        }
        // return null after all attempts failed
        log.complain("FAILURE: all attempts to connect to the debugee VM failed");
        return null;
    }

    private Connector findConnector(String connectorName) {
        List connectors = Bootstrap.virtualMachineManager().allConnectors();
        Iterator iter = connectors.iterator();
        while (iter.hasNext()) {
            Connector connector = (Connector) iter.next();
            if (connector.name().equals(connectorName)) {
                log.display("Connector name=" + connector.name() +
                    "\n\tdescription=" + connector.description() +
                    "\n\ttransport=" + connector.transport().name());
                return connector;
            }
        }
        throw new Error("No appropriate connector: " + connectorName);
    }
}
