package mojo;

import java.util.StringTokenizer;
import java.util.Properties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.model.Profile;

import org.apache.maven.project.MavenProject;

import org.apache.maven.artifact.Artifact;


import org.apache.maven.repository.RepositorySystem;

import org.apache.maven.artifact.resolver.filter.ArtifactFilter;

import org.codehaus.plexus.logging.Logger;

/**
 * Profile utility.
 *
 * @author Cedric Chantepie
 */
public final class ProfileUtility {
    // --- Properties ---

    /**
     * Maven logger
     */
    private final Logger log;

    /**
     * Current project.
     */
    private final MavenProject project;

    /**
     * Prefix for profile properties defining dependencies.
     */
    private final String prefix;

    /**
     * Used to look up Artifacts in the remote repository.
     */
    private final RepositorySystem repoSys;

    // --- Constructors ---

    /**
     * Bulk constructor.
     */
    public ProfileUtility(final Logger log, 
                          final MavenProject project, 
                          final String prefix, 
                          final RepositorySystem repoSys) {

        this.log = log;
        this.project = project;
        this.prefix = prefix;
        this.repoSys = repoSys;
    } // end of <init>

    // ---

    /**
     * Returns matching properties from active profile.
     *
     * @param key Property key
     * @return Property value(s)
     */
    public List<String> getProfileProperty(final String key) {
	final List<Profile> profiles = this.project.getActiveProfiles();

	if (profiles == null || profiles.isEmpty()) {
	    log.warn("No profile");
	    log.debug("profiles = " + profiles);

	    return new ArrayList<String>();
	} // end of if

	// ---

	final ArrayList<String> values = new ArrayList<String>();

	Properties props;
	String value;
	for (final Profile profile : profiles) {
            props = profile.getProperties();

            if (props == null) {
                log.debug("No profile properties: " + profile.getId());

                continue;
            } // end of if

	    // ---

	    log.debug("properties = " + props);

            value = props.getProperty(key);

            if (value == null) {
                continue; // Skip
            } // end of if

            // ---

            values.add(value);
        } // end of for

        return values;
    } // end of getProfileProperty

    /**
     * Returns extra artifact, defined under any profile property,
     * matching prefix.
     */
    public Set<Artifact> getProfileArtifacts(final ArtifactFilter filter) {
        if (filter == null) {
            throw new IllegalArgumentException();
        } // end of if

        // ---

	final List<Profile> profiles = this.project.getActiveProfiles();

	if (profiles == null || profiles.isEmpty()) {
	    log.warn("No profile");
	    log.debug("profiles = " + profiles);

	    return new HashSet<Artifact>();
	} // end of if

	// ---

	final HashSet<Artifact> profileArtifacts = new HashSet<Artifact>();

	Properties props;
	String value;
	StringTokenizer vtok;
        Artifact artifact = null;
	for (final Profile profile : profiles) {
            props = profile.getProperties();

            if (props == null) {
                log.debug("No profile properties: " + profile.getId());

                continue;
            } // end of if

	    // ---

	    log.debug("properties = " + props);
	    
	    for (final String key : props.stringPropertyNames()) {
		log.debug("property key = " + key);
                
		if (!key.startsWith(this.prefix)) {
		    continue;
		} // end of if
                
		// ---
                
		value = props.getProperty(key);
                
		log.debug("Find matching property: " + 
                          key + " = " + value);
		
		vtok = new StringTokenizer(value, " ");

		while (vtok.hasMoreTokens()) {
                    try {
                        artifact = this.createArtifact(vtok.nextToken());
                    } catch (Exception e) {
                        throw new IllegalStateException("Fails to create profile artifact", e);
                    } // end of catch

                    if (!filter.include(artifact)) {
                        log.warn("Exclude profile artifact: " + artifact);
                        continue; // Skip
                    } // end of if

                    // ---

                    profileArtifacts.add(artifact);
		} // end of while
	    } // end of for
	} // end of while

        return profileArtifacts;
    } // end of getProfileArtifacts

    /**
     * Creates artifact according string |specification|,
     * using format matching either `groupId:artifactId:version:type:scope` 
     * or `groupId:artifactId:version:type`.
     *
     * @param specification Profile artifact specification
     * @return Resolution result
     */
    public Artifact createArtifact(final String specification) {

        if (specification == null) {
            throw new IllegalArgumentException("Empty artifact specification: " +  specification);
            
        } // end of if

        // ---

	final StringTokenizer ptok = new StringTokenizer(specification, ":");
        final int pc = ptok.countTokens();

        if (pc < 4) {
            throw new IllegalArgumentException("Invalid artifact specification: Invalid token count (" + pc + "): " + specification);

        } // end of if

        // ---
        
        log.debug("artifact specification = " + specification);
        
        final String groupId = ptok.nextToken();
        final String artifactId = ptok.nextToken();
        final String version = ptok.nextToken();
        final String type = ptok.nextToken();

        final Artifact artifact = (!ptok.hasMoreTokens()) 
            ? this.repoSys.createArtifact(groupId, artifactId, version, type)
            : this.repoSys.createArtifact(groupId, 
                                          artifactId, 
                                          version, 
                                          ptok.nextToken()/* scope */,
                                          type);

        log.debug("profile artifact = " + artifact);

        return artifact;
    } // end of createArtifact
} // end of class ProfileUtility
