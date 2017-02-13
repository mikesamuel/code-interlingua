# Template Tests

Each sub-directory in the `template-tests` directory is a testcase for the
template evaluator.

`TemplateEvaluationEndToEndTest.java` is what ends up running this.

## Files

### `*.java` Templates

Java files with embedded template code.
Supporting Java code that needs to be on the classpath for a test to run
should be under `src/test/java` instead.

### `*.json` Inputs
Input bundles in JSON format of the form

### `*.out` Expected Outputs

For each input json file, *X*`.json`, *X*`.out` is the result of applying the
templates to the data in *X*`.json`, rendering the result to Java
and concatentaing the results together with a delimiter in between.

### `expected.log`

If present, any expected errors or warnings.
