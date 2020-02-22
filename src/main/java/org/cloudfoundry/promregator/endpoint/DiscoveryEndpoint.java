package org.cloudfoundry.promregator.endpoint;

import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.cloudfoundry.promregator.config.PromregatorConfiguration;
import org.cloudfoundry.promregator.discovery.CFMultiDiscoverer;
import org.cloudfoundry.promregator.scanner.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonGetter;

@RestController
@RequestMapping(EndpointConstants.ENDPOINT_PATH_DISCOVERY)
public class DiscoveryEndpoint {

	private static final Logger log = LoggerFactory.getLogger(DiscoveryEndpoint.class);
	
	private final CFMultiDiscoverer cfDiscoverer;
	private final PromregatorConfiguration promregatorConfiguration;

	public DiscoveryEndpoint(CFMultiDiscoverer cfDiscoverer, PromregatorConfiguration promregatorConfiguration) {
		this.cfDiscoverer = cfDiscoverer;
		this.promregatorConfiguration = promregatorConfiguration;
	}

	public static class DiscoveryLabel {
		private String targetPath;
		private String orgName;
		private String spaceName;
		private String applicationName;
		private String applicationId;
		private String instanceNumber;
		private String instanceId;
		private String api;

		public DiscoveryLabel(String path) {
			super();
			this.targetPath = path;
		}
		
		public DiscoveryLabel(String path, Instance instance) {
			this(path);
			
			this.orgName = instance.getTarget().getOrgName();
			this.spaceName = instance.getTarget().getSpaceName();
			this.applicationName = instance.getTarget().getApplicationName();
			this.applicationId = instance.getApplicationId();
			this.instanceNumber = instance.getInstanceNumber();
			this.instanceId = instance.getInstanceId();
			this.api = instance.getTarget().getOriginalTarget().getApi();
		}
		
		@JsonGetter("__meta_promregator_target_path")
		public String getTargetPath() {
			return targetPath;
		}

		@JsonGetter("__meta_promregator_target_orgName")
		public String getOrgName() {
			return orgName;
		}

		@JsonGetter("__meta_promregator_target_spaceName")
		public String getSpaceName() {
			return spaceName;
		}

		@JsonGetter("__meta_promregator_target_applicationName")
		public String getApplicationName() {
			return applicationName;
		}

		@JsonGetter("__meta_promregator_target_applicationId")
		public String getApplicationId() {
			return applicationId;
		}

		@JsonGetter("__meta_promregator_target_instanceNumber")
		public String getInstanceNumber() {
			return instanceNumber;
		}

		@JsonGetter("__meta_promregator_target_instanceId")
		public String getInstanceId() {
			return instanceId;
		}

		@JsonGetter("__meta_promregator_target_api")
		public String getApi() {
			return this.api;
		}

		@JsonGetter("__metrics_path__")
		public String getMetricsPath() {
			return this.targetPath;
		}
	}
	
	public static class DiscoveryResponse {
		private String[] targets;
		
		private DiscoveryLabel labels;

		public DiscoveryResponse(String[] targets, DiscoveryLabel labels) {
			super();
			this.targets = targets.clone();
			this.labels = labels;
		}

		public String[] getTargets() {
			return targets.clone();
		}

		public DiscoveryLabel getLabels() {
			return labels;
		}
	}
	
	@GetMapping(produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<DiscoveryResponse[]> getDiscovery(HttpServletRequest request) {
		
		List<Instance> instances = this.cfDiscoverer.discover(null, null);
		if (instances == null || instances.isEmpty()) {
			return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
		}

		String myHostName = this.promregatorConfiguration.getDiscovery().getHostname();
		int myPort = this.promregatorConfiguration.getDiscovery().getPort();
		String localHostname = myHostName != null ? myHostName : request.getLocalName();
		int localPort = myPort != 0 ? myPort : request.getLocalPort();
		final String[] targets = { String.format("%s:%d", localHostname, localPort) };
		
		log.info(String.format("Using scraping target %s in discovery response", targets[0]));
		
		List<DiscoveryResponse> result = new LinkedList<>();
		for (Instance instance : instances) {
			
			String path = String.format(EndpointConstants.ENDPOINT_PATH_SINGLE_TARGET_SCRAPING+"/%s/%s", instance.getApplicationId(), instance.getInstanceNumber());
			DiscoveryLabel dl = new DiscoveryLabel(path, instance);
			
			DiscoveryResponse dr = new DiscoveryResponse(targets, dl);
			result.add(dr);
		}
		
		if (this.promregatorConfiguration.getDiscovery().getOwnMetricsEndpoint()) {
			// finally, also add our own metrics endpoint
			DiscoveryLabel dl = new DiscoveryLabel(EndpointConstants.ENDPOINT_PATH_PROMREGATOR_METRICS);
			result.add(new DiscoveryResponse(targets, dl));
		}
		
		log.info(String.format("Returning discovery document with %d targets", result.size()));
		
		return new ResponseEntity<>(result.toArray(new DiscoveryResponse[0]), HttpStatus.OK);
	}
}
