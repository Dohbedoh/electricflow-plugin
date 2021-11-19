package org.jenkinsci.plugins.electricflow;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.SchemeRequirement;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collections;
import java.util.function.Function;

import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;

public class Credential extends AbstractDescribableImpl<Credential> {
  
  private String credentialId;

  @DataBoundConstructor
  public Credential(String credentialId) {
    this.credentialId = credentialId;
  }

  private static StandardUsernamePasswordCredentials getStandardUsernamePasswordCredentialsById(
      String credentialsId) {
    if (credentialsId == null) {
      return null;
    }

    return CredentialsMatchers.firstOrNull(
        lookupCredentials(
            StandardUsernamePasswordCredentials.class,
            Jenkins.get(),
            ACL.SYSTEM,
            new SchemeRequirement("http"),
            new SchemeRequirement("https")),
        CredentialsMatchers.withId(credentialsId));
  }

  public String getCredentialId(EnvReplacer envReplacer) {
    return envReplacer == null ? getCredentialId() : envReplacer.expandEnv(getCredentialId());
  }

  public String getCredentialId() {
    return credentialId;
  }

  public void setCredentialId(String credentialId) {
    this.credentialId = credentialId;
  }

  public StandardUsernamePasswordCredentials getUsernamePasswordBasedOnCredentialId(
      EnvReplacer envReplacer, Run run) {
    return getUsernamePasswordBasedOnCredentialId(envReplacer, credentialsId ->
        run == null
            ? getStandardUsernamePasswordCredentialsById(credentialsId)
            : CredentialsProvider.findCredentialById(
                credentialsId, 
                StandardUsernamePasswordCredentials.class, 
                run, 
                Collections.emptyList())
    );
  }

  public StandardUsernamePasswordCredentials getUsernamePasswordBasedOnCredentialId(
      EnvReplacer envReplacer, Item item) {
    
    return getUsernamePasswordBasedOnCredentialId(envReplacer, credentialsId -> 
        item == null
          ? getStandardUsernamePasswordCredentialsById(credentialsId)
          : CredentialsMatchers.firstOrNull(
              lookupCredentials(
                  StandardUsernamePasswordCredentials.class,
                  item,
                  ACL.SYSTEM,
                  new SchemeRequirement("http"),
                  new SchemeRequirement("https")),
              CredentialsMatchers.withId(credentialsId))
    );
  }

  private StandardUsernamePasswordCredentials getUsernamePasswordBasedOnCredentialId(
      EnvReplacer envReplacer, Function<String, StandardUsernamePasswordCredentials> credSupplier) {
    String resolvedCredentialsId = getCredentialId(envReplacer);
    if (resolvedCredentialsId == null) {
      return null;
    }
    return credSupplier.apply(getCredentialId(envReplacer));
  }

  @Extension
  public static class DescriptorImpl extends Descriptor<Credential> {

    public static ListBoxModel doFillCredentialIdItems(@AncestorInPath Item item) {
      
      if (item == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
        return new StandardListBoxModel();
      }
      if (item != null 
          && !item.hasPermission(Item.EXTENDED_READ) /*implied by Item.CONFIGURE*/ 
          && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
        return new StandardListBoxModel();
      }

      return new StandardUsernameListBoxModel()
          .includeEmptyValue()
          .includeMatchingAs(
              ACL.SYSTEM,
              item,
              StandardUsernamePasswordCredentials.class,
              Collections.emptyList(),
              CredentialsMatchers.always());
    }

    @Override
    public String getDisplayName() {
      return "Credential";
    }
  }
}
