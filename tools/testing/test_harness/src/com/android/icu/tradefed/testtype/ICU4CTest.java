/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.icu.tradefed.testtype;

import com.android.ddmlib.FileListingService;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IRuntimeHintProvider;
import com.android.tradefed.testtype.ITestCollector;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.Collectors;


/** A Test that runs a native test package on given device. */
@OptionClass(alias = "icu4c")
public class ICU4CTest
        implements IDeviceTest,
    ITestFilterReceiver,
    IRemoteTest,
    IAbiReceiver,
    ITestCollector,
    IRuntimeHintProvider {

    static final String DEFAULT_NATIVETEST_PATH = "/data/local/tmp";

    static final String TEMPLATE_TEST_CASE_FORMAT_STRING =
            "\t<testcase classname=\"%s\" name=\"%s\" time=\"%s\"/>";
    static final String TEST_NAMES_STARTING_STRING = "Test names:";
    static final String DIVIDING_LINE = "-----------";

    private ITestDevice mDevice = null;
    private IAbi mAbi = null;

    @Option(name = "module-name", description = "The name of the native test module to run.")
    private String mTestModule = null;

    @Option(name = "command-filter-prefix",
        description = "The prefix required for each test filter when running the shell command")
    private String mCommandFilterPrefix = "";

    @Option(
        name = "native-test-timeout",
        description =
                "The max time in ms for the test to run. "
                        + "Test run will be aborted if any test takes longer."
    )
    private int mMaxTestTimeMs = 60 * 1000;

    @Option(name = "run-test-as", description = "User to execute test binary as.")
    private String mRunTestAs = null;

    @Option(
        name = "runtime-hint",
        description = "The hint about the test's runtime.",
        isTimeVal = true
    )
    private long mRuntimeHint = 60000; // 1 minute

    @Option(
        name = "no-fail-data-errors",
        description = "Treat data load failures as warnings, not errors."
    )
    private boolean mNoFailDataErrors = false;

    @Option(
        name = "include-filter",
        description = "The ICU-specific positive filter of the test names to run."
    )
    private Set<String> mIncludeFilters = new LinkedHashSet<>();

    @Option(name = "collect-tests-only",
            description = "Only invoke the instrumentation to collect list of applicable test "
                    + "cases. All test run callbacks will be triggered, but test execution will "
                    + "not be actually carried out.")
    private boolean mCollectTestsOnly = false;

    @Option(
        name = "exclude-filter",
        description = "The ICU-specific negative filter of the test names to run."
    )
    private Set<String> mExcludeFilters = new LinkedHashSet<>();

    private static final String TEST_FLAG_NO_FAIL_DATA_ERRORS = "-w";
    private static final String TEST_FLAG_XML_OUTPUT = "-x";

    /** {@inheritDoc} */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /** {@inheritDoc} */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /** {@inheritDoc} */
    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    /** {@inheritDoc} */
    @Override
    public IAbi getAbi() {
        return mAbi;
    }

    /**
     * Set the Android native test module to run.
     *
     * @param moduleName The name of the native test module to run
     */
    public void setModuleName(String moduleName) {
        mTestModule = moduleName;
    }

    /**
     * Get the Android native test module to run.
     *
     * @return the name of the native test module to run, or null if not set
     */
    public String getModuleName() {
        return mTestModule;
    }

    /** Set the max time in ms for the test to run. */
    @VisibleForTesting
    void setMaxTestTimeMs(int timeout) {
        mMaxTestTimeMs = timeout;
    }

    /** {@inheritDoc} */
    @Override
    public long getRuntimeHint() {
        return mRuntimeHint;
    }

    /** {@inheritDoc} */
    @Override
    public void addIncludeFilter(String filter) {
        mIncludeFilters.add(filter);
    }

    /** {@inheritDoc} */
    @Override
    public void addAllIncludeFilters(Set<String> filters) {
        mIncludeFilters.addAll(filters);
    }

    /** {@inheritDoc} */
    @Override
    public void addExcludeFilter(String filter) {
        mExcludeFilters.add(filter);
    }

    /** {@inheritDoc} */
    @Override
    public void addAllExcludeFilters(Set<String> filters) {
        mExcludeFilters.addAll(filters);
    }

    @Override
    public Set<String> getIncludeFilters() {
        return mIncludeFilters;
    }

    @Override
    public Set<String> getExcludeFilters() {
        return mExcludeFilters;
    }

    /** {@inheritDoc} */
    @Override
    public void clearExcludeFilters() {
        mExcludeFilters.clear();
    }

    public boolean getCollectTestsOnly() {
        return mCollectTestsOnly;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCollectTestsOnly(boolean collectTests) {
        mCollectTestsOnly = collectTests;
    }

    @Override
    public void clearIncludeFilters() {
        mIncludeFilters.clear();
    }

    public void setCommandFilterPrefix(String s) {
        if (s == null) {
            throw new NullPointerException("CommandFilterPrefix can't be null");
        }
        mCommandFilterPrefix = s;
    }

    public String getCommandFilterPrefix() {
        return mCommandFilterPrefix;
    }

    /**
     * Gets the path where native tests live on the device.
     *
     * @return The path on the device where the native tests live.
     */
    private String getTestPath() {
        StringBuilder testPath = new StringBuilder(DEFAULT_NATIVETEST_PATH);

        testPath.append(FileListingService.FILE_SEPARATOR);
        testPath.append(mTestModule);

        return testPath.toString();
    }

    protected boolean isDeviceFileExecutable(String fullPath) throws DeviceNotAvailableException {
        CommandResult commandResult = mDevice.executeShellV2Command(String.format("[ -x %s ]",
            fullPath));
        return commandResult.getExitCode() == 0;
    }

    /**
     * Run the given test binary.
     *
     * @param testDevice the {@link ITestDevice}
     * @param fullPath absolute file system path to test binary on device
     * @param xmlOutputPath absolute file system path to the XML result file
     * @throws DeviceNotAvailableException if the device isn't available.
     */
    private CommandResult doRunTest(
            final ITestDevice testDevice, final String fullPath, final String xmlOutputPath)
            throws DeviceNotAvailableException {
        String cmd = getTestCmdLine(fullPath, xmlOutputPath);
        CLog.i("Running ICU4C test %s on %s", cmd, testDevice.getSerialNumber());
        CommandResult commandResult =
                testDevice.executeShellV2Command(
                        cmd,
                        mMaxTestTimeMs /* maxTimeToShellOutputResponse */,
                        TimeUnit.MILLISECONDS,
                        0 /* retryAttempts */);
        CLog.d(
                "%s executed with an exit code of %d and status code  %s",
                fullPath, commandResult.getExitCode(), commandResult.getStatus().name());

        if (commandResult.getExitCode() != 0) {
            CLog.e("Command stdout:\n " + commandResult.getStdout());
            CLog.e("Command stderr:\n " + commandResult.getStderr());
            throw new IllegalStateException(
                    String.format(
                            "%s non-zero exit code %d", fullPath, commandResult.getExitCode()));
        }

        if (commandResult.getStatus() != CommandStatus.SUCCESS) {
            CLog.e("Command stdout:\n " + commandResult.getStdout());
            CLog.e("Command stderr:\n " + commandResult.getStderr());
            throw new IllegalStateException(
                    String.format(
                            "%s exits with status %s", fullPath, commandResult.getStatus().name()));
        }

        return commandResult;
    }

    private boolean isMatched(final String pattern, final String content) {
        return Pattern.matches(pattern, content);
    }

    private boolean isTestNameValid(final String name) {
        return !(name.contains("(") || name.contains(")"));
    }

    /**
     * Check the line can be skipped or not
     *
     * @param line the text of one line in stdout
     * @return if the line can be skipped
     */
    private boolean canSkip(final String line) {
        if (line.isBlank()) {
            return true;
        }
        if (line.contains(TEST_NAMES_STARTING_STRING) || line.contains(DIVIDING_LINE)) {
            return true;
        }

        // Some testcase printed in format "description : name", the ":" is printed at the end
        // of first line, the name is printed in the second line, ignore the first line.
        return line.endsWith(":");
    }

    /**
     * Check the line is the end line of a test suite or not
     *
     * @param testSuite the current test suite name
     * @param line      the text of one line in stdout
     * @return the status of the check.
     */
    private boolean isIntltestEndLine(final String testSuite, final String line,
            List<String> testSuites) {

        String endOKPattern = ".*" + "\\}" + "\\s+" + "OK:" + "\\s+" + testSuite + ".*";
        if (isMatched(endOKPattern, line)) {
            return true;
        }

        // Most of intltest test cases print "OK:" at the end of the std output, but some cases
        // print "ERRORS" at the end of std output. For the test cases are not really executed,
        // so ignore the "ERRORS", just clear test cases those were incorrectly collected from
        // the test case's std output.
        String endErrorPattern = ".*" + "\\}" + "\\s+" + "ERRORS" + ".*" + testSuite + ".*";
        if (isMatched(endErrorPattern, line)) {
            testSuites.clear();
            return true;
        }
        return false;
    }

    boolean isIntltestStartLine(final String testSuite, final String line) {
        String startPattern = "\\s+" + testSuite + "\\s+" + "\\{";
        return isMatched(startPattern, line);
    }

    /**
     * Parse the given test binary's stdout to collect test cases.
     *
     * @param stdout    test binary's stdout
     * @param testSuite the current testSuite name
     * @return a list of testsuites or testcases.
     */
    private List<String> parseIntltestStdout(final String stdout, final String testSuite) {
        List<String> testSuites = new ArrayList<>();
        Stream<String> lines = stdout.lines();
        Iterator<String> lineIterator = lines.iterator();
        boolean start = false;
        while (lineIterator.hasNext()) {
            String line = lineIterator.next();
            if (!start) {
                start = isIntltestStartLine(testSuite, line);
                continue;
            }

            if (canSkip(line)) {
                continue;
            }

            if (isIntltestEndLine(testSuite, line, testSuites)) {
                break;
            }

            String caseName = line.trim();

            // Many test suites' output format are "test description : test name",
            // but a special case is the "DataDrivenFormatTest/TestMoreDateParse",
            // which has not ":" between test description and test name.
            // e.g.
            // "round/trip to format.)         TestMoreDateParse"
            if (caseName.contains(":")) {
                caseName = caseName.substring(caseName.lastIndexOf(":") + 1);
            } else if (caseName.contains("TestMoreDateParse")) {
                caseName = "TestMoreDateParse";
            }

            if (isTestNameValid(caseName.trim())) {
                testSuites.add(caseName.trim());
            }
        }

        CLog.d("parseIntltestStdout  return testSuites: " + testSuites.size());
        return testSuites;
    }

    /**
     * Collect all sub testsuites and testcases .
     *
     * @param testDevice     the {@link ITestDevice}
     * @param fullPath       absolute file system path to test binary on device
     * @param parentSuite    parent testsuite
     * @param testSuite      current testsuite
     * @param collectResults a list of collected testsuites and testcases.
     */
    private void collectIntltestSubtest(final ITestDevice testDevice, final String fullPath,
            final String parentSuite, final String testSuite, List<String> collectResults) {

        String stdout;
        String suite = testSuite;
        if (!parentSuite.isEmpty()) {
            suite = parentSuite + "/" + testSuite;
        }
        String cmd = fullPath + " " + suite + "/LIST";

        // Some special cases have only description in std output, the description
        // can easily be mistaken for a sub test case.
        // e.g.
        //     testPermutations {
        //           Quick mode: stopped after 1095 lines
        //     } OK:   testPermutations  (19ms)
        if (suite.equals("utility/LocaleMatcherTest/testDataDriven")
                || suite.equals("format/NumberTest/NumberPermutationTest/testPermutations")) {
            String result = String.format(TEMPLATE_TEST_CASE_FORMAT_STRING, suite,
                    suite.substring(suite.lastIndexOf("/") + 1),
                    "0.000000");
            collectResults.add(result);
            return;
        }

        try {
            CommandResult commandResult = executeCollectTestShellCommand(testDevice, cmd);
            stdout = commandResult.getStdout();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        if (stdout.isBlank()) {
            throw new IllegalStateException("command failed: " + cmd);
        }

        List<String> testItems = parseIntltestStdout(stdout, testSuite);
        for (String item : testItems) {
            collectIntltestSubtest(testDevice, fullPath, suite, item, collectResults);
        }

        String name = suite.substring(suite.lastIndexOf("/") + 1);
        String classname = suite;
        if (name.equals(suite)) {
            classname = "/" + name;
        }
        String result = String.format(TEMPLATE_TEST_CASE_FORMAT_STRING, classname, name, "0.000000");
        collectResults.add(result);
    }

    private CommandResult executeCollectTestShellCommand(final ITestDevice testDevice,
            final String cmd)
            throws DeviceNotAvailableException {
        CLog.d("executeCollectTestShellCommand: " + cmd);
        CommandResult commandResult =
                testDevice.executeShellV2Command(
                        cmd,
                        mMaxTestTimeMs /* maxTimeToShellOutputResponse */,
                        TimeUnit.MILLISECONDS,
                        0 /* retryAttempts */);

        if (commandResult.getExitCode() != 0) {
            throw new IllegalStateException(
                    String.format(
                            "non-zero exit code %d", commandResult.getExitCode()));
        }
        if (commandResult.getStatus() != CommandStatus.SUCCESS) {
            throw new IllegalStateException(
                    String.format(
                            "exits with status %s", commandResult.getStatus().name()));
        }
        return commandResult;
    }

    /**
     * Run the test binary Cintltst to collect test cases.
     *
     * @param testDevice    the {@link ITestDevice}
     * @param fullPath      absolute file system path to test binary on device
     * @param file          temp output file object.
     */
    private CommandResult doCollectCintltst(
            final ITestDevice testDevice, final String fullPath, final File file)
            throws DeviceNotAvailableException {
        String cmd = fullPath + " -l";
        CommandResult commandResult = executeCollectTestShellCommand(testDevice, cmd);
        String stdout = commandResult.getStdout();
        List<String> collectResults = new ArrayList<>();
        List<String> lines = stdout.lines().collect(Collectors.toList());
        for (String line : lines) {
            if (line.lastIndexOf("---") > 0) {
                // Follow "run test", only collect testcase, ignore testsuite
                if (!line.endsWith("/")) {
                    String content = line.substring(line.lastIndexOf("---") + 3).trim();
                    String result = String.format(TEMPLATE_TEST_CASE_FORMAT_STRING, content,
                            content, "0.000000");
                    collectResults.add(result);
                }
            }
        }
        outputCollectResults(collectResults, fullPath, file);
        return commandResult;
    }

    /**
     * Output the collected results to temp file.
     *
     * @param testcases the collected testcases
     * @param fullPath  absolute file system path to test binary on device
     * @param file      temp output file object.
     */
    private void outputCollectResults(final List<String> testcases, final String fullPath,
            final File file) {
        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file))) {
            bufferedWriter.write(String.format("<testsuite name=\"%s\">", fullPath));
            for (String testcase : testcases) {
                bufferedWriter.newLine();
                bufferedWriter.write(testcase);
            }
            bufferedWriter.newLine();
            bufferedWriter.write("</testsuite>");
        } catch (IOException e) {
            // Handling any I/O exceptions
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Run the test binary Intltest to collect test cases.
     *
     * @param testDevice the {@link ITestDevice}
     * @param fullPath   absolute file system path to test binary on device
     * @param file       temp output file object.
     */
    private CommandResult doCollectIntltest(
            final ITestDevice testDevice, final String fullPath, final File file)
            throws DeviceNotAvailableException {
        String cmd = fullPath + " LIST";
        CLog.i("Running doCollectIntltest test %s on %s", cmd, testDevice.getSerialNumber());
        CommandResult commandResult = executeCollectTestShellCommand(testDevice, cmd);

        Stream<String> lines = commandResult.getStdout().lines();
        Iterator<String> lineIterator = lines.iterator();
        List<String> testSuites = new ArrayList<>();
        List<String> collectResults = new ArrayList<>();
        boolean start = false;
        while (lineIterator.hasNext()) {
            String line = lineIterator.next();
            if (!start) {
                start = line.contains(TEST_NAMES_STARTING_STRING);
                continue;
            }
            if (line.isBlank()) {
                break;
            }
            if (line.contains(DIVIDING_LINE)) {
                continue;
            }
            testSuites.add(line.trim());
        }

        for (String suite : testSuites) {
            collectIntltestSubtest(testDevice, fullPath, "", suite, collectResults);
        }
        outputCollectResults(collectResults, fullPath, file);
        return commandResult;
    }

    /**
     * Run the given test binary and parse XML results
     *
     * <p>This methods typically requires the filter for .tff and .xml files, otherwise it will post
     * some unwanted results.
     *
     * @param testDevice the {@link ITestDevice}
     * @param fullPath absolute file system path to test binary on device
     * @param listener the {@link ITestRunListener}
     * @throws DeviceNotAvailableException if the device isn't available.
     */
    private void runTest(
            final ITestDevice testDevice, final String fullPath, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        CLog.i("Running runTest path: %s", fullPath);
        String xmlFullPath = fullPath + "_res.xml";

        try {
            CommandResult commandResult;
            String testRunName = fullPath.substring(fullPath.lastIndexOf("/") + 1);
            File tmpOutput = FileUtil.createTempFile(testRunName, ".xml");

            // cintltst is a test binary for C++ tests, intltest is a test binary for Java tests.
            if (mCollectTestsOnly && fullPath.endsWith("/cintltst")) {
                commandResult = doCollectCintltst(testDevice, fullPath, tmpOutput);
            } else if (mCollectTestsOnly && fullPath.endsWith("/intltest")) {
                commandResult = doCollectIntltest(testDevice, fullPath, tmpOutput);
            } else {
                commandResult = doRunTest(testDevice, fullPath, xmlFullPath);
            }

            if (!mCollectTestsOnly) {
                // Pull the result file, may not exist if issue with the test.
                testDevice.pullFile(xmlFullPath, tmpOutput);
            }

            ICU4CXmlResultParser parser = new ICU4CXmlResultParser(mTestModule,
                testRunName, listener);

            parser.parseResult(tmpOutput, commandResult);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            // Clean the file on the device
            testDevice.executeShellCommand("rm " + xmlFullPath);
        }
    }

    /**
     * Helper method to build the test command to run.
     *
     * @param fullPath absolute file system path to test binary on device
     * @param xmlPath absolute file system path to the XML reult file on device
     * @return the shell command line to run for the test
     */
    protected String getTestCmdLine(String fullPath, String xmlPath) {
        List<String> args = new LinkedList<>();

        // su to requested user
        if (mRunTestAs != null) {
            args.add(String.format("su %s", mRunTestAs));
        }

        args.add(fullPath);

        if (mNoFailDataErrors) {
            args.add(TEST_FLAG_NO_FAIL_DATA_ERRORS);
        }

        args.add(TEST_FLAG_XML_OUTPUT);
        args.add(xmlPath);

        String cmd = String.join(" ", args);

        List<String> includeFilters = preprocessIncludeFilters();
        if (!includeFilters.isEmpty()) {
            cmd += " " + String.join(" ", includeFilters);
        }

        return cmd;
    }

    private List<String> preprocessIncludeFilters() {
        Set<String> includeFilters = mIncludeFilters;
        List<String> results = new ArrayList<>();
        for (String filter : includeFilters) {
            if (!filter.startsWith(mTestModule)) {
                CLog.i("Ignore positive filter which does not contain module prefix \"%s\":%s",
                    mTestModule, filter);
                continue;
            }
            String modifiedFilter = filter.substring(mTestModule.length());
            if (filter.isEmpty()) {
                // Ignore because it intends to run all tests when the filter is the module name.
                continue;
            }
            // Android / tradefed uses '.' as package separator, but ICU4C tests use '/'.
            modifiedFilter = modifiedFilter.replace('.', '/');

            if (modifiedFilter.charAt(0) != '/' || modifiedFilter.length() == 1) {
                CLog.i("Ignore invalid filter:%s", filter);
                continue;
            }
            modifiedFilter = mCommandFilterPrefix + modifiedFilter.substring(1);
            results.add(modifiedFilter);
        }
        return results;
    }

    /** {@inheritDoc} */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }

        String testPath = getTestPath();
        if (!mDevice.doesFileExist(testPath)) {
            throw new IllegalStateException(
                    String.format(
                            "Could not find native test binary %s in %s!",
                            testPath, mDevice.getSerialNumber()));
        }
        if (!isDeviceFileExecutable(testPath)) {
            throw new IllegalStateException(
                    String.format(
                            "%s exists but is not executable in %s.",
                            testPath, mDevice.getSerialNumber()));
        }
        if (!mExcludeFilters.isEmpty()) {
            // Log a message instead of throwing IllegalStateException. http://b/213284403
            CLog.w("ICU4C test suites do not support exclude filters: %s",
                Arrays.toString(mExcludeFilters.toArray()));
        }
        runTest(mDevice, testPath, listener);
    }
}
