package com.carddemo.batch.orchestration.core;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * {@code java -jar carddemo.jar --spring.batch.job.name=CLOSEFIL} behaves like a JCL submit: runs
 * the job without the web server and exits with the JCL condition code.
 */
public class BatchCliEnvironmentPostProcessor implements EnvironmentPostProcessor {

  static final String SOURCE = "carddemoBatchCli";
  private static final String CLI_ARGS = "commandLineArgs";

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
    String job = env.getProperty("spring.batch.job.name");
    if (job == null || job.isBlank()) {
      return;
    }
    Map<String, Object> props = new HashMap<>();
    props.put("spring.batch.job.enabled", "true");
    props.put("spring.main.web-application-type", "none");
    props.put("carddemo.orchestration.cli.exit", "true");
    // Above application.yml (which disables batch jobs), below the command line.
    var sources = env.getPropertySources();
    var source = new MapPropertySource(SOURCE, props);
    if (sources.contains(CLI_ARGS)) {
      sources.addAfter(CLI_ARGS, source);
    } else {
      sources.addFirst(source);
    }
  }
}
