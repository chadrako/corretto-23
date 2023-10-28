/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @bug 8238175
 * @summary Check that having a method with an interface type in its throws
 *          clause does not cause the JVM to assert nor throw an exception.
 *          Also, test that logging can be used to print a message about bogus
 *          classes in method throws clauses.
 * @requires vm.flagless
 * @library /test/lib
 * @compile CheckedExceptions.jcod
 * @run driver TestCheckedExceptions
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestCheckedExceptions {

    public static void main(String... args) throws Exception {

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-Xlog:exceptions=warning", "CheckedExceptions");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Class I in throws clause of method void CheckedExceptions.main(java.lang.String[]) is not a subtype of class java.lang.Throwable");
        output.shouldHaveExitValue(0);
    }
}
