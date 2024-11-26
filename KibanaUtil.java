import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONObject;

@Log4j2
public class KibanaUtil {

  private static void postToKibana(File file, String date) throws Exception {
    log.info("post To Kibana");

    ObjectMapper mapper = new ObjectMapper();
    String json = FileUtils.readFile(file);
    String data = "{\"data\":" + new JSONArray(json).toString() + "}";

    List<Map> features = JsonPath.parse(json).read("$[*]");
    String projectName = file.getParentFile().getParentFile().getName();

    StringBuilder[] headers = new StringBuilder[5];
    for (int i = 0; i < headers.length; i++) {
      headers[i] = new StringBuilder();
    }

    for (Map feature : features) {
      JSONObject feat = new JSONObject(feature);
      log.info(String.valueOf(feat));

      AnalysisObjectNew ao = new AnalysisObjectNew();
      AnalysisObject ao1 = new AnalysisObject();
      AnalysisObjectSuite suiteObject = new AnalysisObjectSuite();

      ao.setProjectName(projectName);
      ao1.setProjectName(projectName);
      suiteObject.setProjectName(projectName);

      ao.Date = date;
      ao1.Date = date;
      suiteObject.Date = date;

      String featureName = JsonPath.parse(feat.toString()).read("$.name");
      ao.FeatureName = featureName;
      ao1.FeatureName = featureName;
      suiteObject.FeatureName = featureName;

      List<Map> scenarios = JsonPath.parse(feat.toString()).read("$.elements[*]");
      for (Map scenario : scenarios) {
        JSONObject sce = new JSONObject(scenario);
        String scenarioName = JsonPath.parse(sce.toString()).read("$.name");

        ao.setScenario(scenarioName);
        ao1.setScenario(scenarioName);
        suiteObject.setScenario(scenarioName);

        List<String> status = JsonPath.parse(sce.toString()).read("steps[*].result.status");
        suiteObject.setPasscount(Collections.frequency(status, "passed"));
        suiteObject.setSkippedcount(Collections.frequency(status, "skipped"));
        suiteObject.setFailcount(Collections.frequency(status, "failed"));
        suiteObject.setTotalcount(status.size());

        ao1.count = 1;
        ao1.status = status.contains("failed") ? "failed" : status.contains("skipped") ? "skipped" : "passed";

        List<String> tags = JsonPath.parse(sce.toString()).read("$..tags[*].name");
        ArrayList<String> formattedTags = new ArrayList<>();
        for (String tag : tags) {
          formattedTags.add(tag.replace("@", "").toUpperCase());
        }
        ao1.setTags(formattedTags);
        suiteObject.setTags(formattedTags);

        List<Map> steps = JsonPath.parse(sce.toString()).read("$.steps[*]");
        for (Map step : steps) {
          JSONObject step_s = new JSONObject(step);
          String stepsName = JsonPath.parse(step_s.toString()).read("$.name");
          String stepStatus = JsonPath.parse(step_s.toString()).read("$.result.status");

          ao.setStatus(stepStatus);
          ao.setCount(1);
          ao.setSteps(stepsName);

          try {
            String error = JsonPath.parse(step_s.toString()).read("$.result.error_message");
            ao.setError(error);
          } catch (Exception ignored) {}

          headers[1].append("{\"index\":{\"_index\":\"automationreport\",\"_type\":\"simple\"}}\n")
              .append(mapper.writeValueAsString(ao)).append("\n");
        }

        headers[2].append("{\"index\":{\"_index\":\"automationscenarioreport\",\"_type\":\"simple\"}}\n")
            .append(mapper.writeValueAsString(ao1)).append("\n");
        headers[0].append("{\"index\":{\"_index\":\"automationreportrecent\",\"_type\":\"simple\"}}\n")
            .append(mapper.writeValueAsString(ao1)).append("\n");
        headers[3].append("{\"index\":{\"_index\":\"automationsuitereport\",\"_type\":\"simple\"}}\n")
            .append(mapper.writeValueAsString(suiteObject)).append("\n");
      }
    }

    headers[4].append("{\"index\":{\"_index\":\"cucumberjson\",\"_type\":\"simple\"}}\n").append(data).append("\n");

    for (StringBuilder header : headers) {
      KibanaUtility.postindex(header.toString());
    }

    log.info("post To Kibana end");
  }

  public static void postAllFile(String path, String date) throws Exception {
    File directory = new File(path);
    log.info(directory.getAbsolutePath());
    File[] files = directory.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isFile() && file.getName().equalsIgnoreCase("cucumber.json")) {
          try {
            postToKibana(file, date);
          } catch (Exception ex) {
            log.error("Fail to post data to kibana: " + ex.getMessage());
          }
        } else if (file.isDirectory()) {
          postAllFile(file.getAbsolutePath(), date);
        }
      }
    }
  }
}