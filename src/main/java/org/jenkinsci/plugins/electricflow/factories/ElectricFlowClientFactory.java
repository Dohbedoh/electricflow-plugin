package org.jenkinsci.plugins.electricflow.factories;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.model.Item;
import hudson.model.Run;
import org.jenkinsci.plugins.electricflow.Configuration;
import org.jenkinsci.plugins.electricflow.Credential;
import org.jenkinsci.plugins.electricflow.ElectricFlowClient;
import org.jenkinsci.plugins.electricflow.EnvReplacer;
import org.jenkinsci.plugins.electricflow.Utils;

import java.util.function.Supplier;

public class ElectricFlowClientFactory {

  public static ElectricFlowClient getElectricFlowClient(
      String configurationName,
      Credential overrideCredential,
      Item item,
      EnvReplacer envReplacer,
      boolean ignoreUnresolvedOverrideCredential) {

    return getElectricFlowClient(
        configurationName,
        overrideCredential == null
            ? null
            : () -> overrideCredential.getUsernamePasswordBasedOnCredentialId(envReplacer, item),
        ignoreUnresolvedOverrideCredential
    );
  }

  public static ElectricFlowClient getElectricFlowClient(
      String configurationName,
      Credential overrideCredential,
      final Run run,
      final EnvReplacer envReplacer,
      boolean ignoreUnresolvedOverrideCredential) {
    
    return getElectricFlowClient(
        configurationName,
        overrideCredential == null 
            ? null 
            : () -> overrideCredential.getUsernamePasswordBasedOnCredentialId(envReplacer, run),
        ignoreUnresolvedOverrideCredential
    );
  }

  private static ElectricFlowClient getElectricFlowClient(
      String configurationName,
      Supplier<StandardUsernamePasswordCredentials> overrideCredentialSupplier,
      boolean ignoreUnresolvedOverrideCredential) {
    Configuration cred = Utils.getConfigurationByName(configurationName);

    if (cred == null) {
      throw new RuntimeException("Cannot find CloudBees CD configuration " + configurationName);
    }

    String electricFlowUrl = cred.getElectricFlowUrl();
    boolean ignoreSslConnectionErrors = cred.getIgnoreSslConnectionErrors();
    String electricFlowApiVersion = cred.getElectricFlowApiVersion();
    String apiVersion = electricFlowApiVersion != null ? electricFlowApiVersion : "";

    String username;
    String password;
    if (overrideCredentialSupplier == null) {
      username = cred.getElectricFlowUser();
      password = cred.getElectricFlowPassword().getPlainText();
    } else {
      StandardUsernamePasswordCredentials creds = overrideCredentialSupplier.get();
      if (creds == null) {
        if (ignoreUnresolvedOverrideCredential) {
          username = cred.getElectricFlowUser();
          password = cred.getElectricFlowPassword().getPlainText();
        } else {
          throw new RuntimeException(
              "Override credentials are not found by provided credential id");
        }
      } else {
        username = creds.getUsername();
        password = creds.getPassword().getPlainText();
      }
    }

    return new ElectricFlowClient(
        electricFlowUrl, username, password, apiVersion, ignoreSslConnectionErrors);
  }
}
