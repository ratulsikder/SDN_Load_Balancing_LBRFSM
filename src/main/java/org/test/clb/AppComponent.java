package org.test.clb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onosproject.cluster.NodeId;
import org.onosproject.mastership.MastershipStore;
import org.onosproject.net.Device;
import org.onosproject.net.Port;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
public class AppComponent {

	private final Logger log = LoggerFactory.getLogger(getClass());
	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected DeviceService deviceService;
	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected MastershipStore mastershipStore;

	// Timer to run Monitoring Module
	Timer timer = new Timer();

	@Activate
	protected void activate() {

		// Controller node(IP) Declaration
		NodeId node1 = new NodeId("172.17.0.5");
		NodeId node2 = new NodeId("172.17.0.6");
		NodeId node3 = new NodeId("172.17.0.7");

		// ArrayList to store Controller object
		ArrayList<Controller> controllers = new ArrayList<>();
		
		// Creating Controller instances and assigning to Array List
		controllers.add(new Controller(node1));
		controllers.add(new Controller(node2));
		controllers.add(new Controller(node3));

		// Getting all Switches(devices) of the Network
		Iterable<Device> devices = deviceService.getDevices();

		// *******Starting Monitoring Module (within TimerTask)********
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				log.info("*** Starts Reporting ***");

				// Initially set Controllers load and its Switches list to zero and blank
				for (Controller controller : controllers) {
					controller.controllerLoad = 0;
					controller.switches.clear();
				}

				// Traversing each Switch (Device) for gathering port delta statistics
				/* Port delta statistics is the difference between previous and current 
				 * traffic of a port of a switch within two reporting time to the controller.
				 * The load of a controller is proportionate to the gross traffic of its
				 * connected switches(devices)
				*/
				for (Device d : devices) {
					List<Port> ports = deviceService.getPorts(d.id());
					long bytes = 0;
					for (Port port : ports) {
						PortStatistics portstat = deviceService.getDeltaStatisticsForPort(d.id(), port.number());
						if (portstat != null) {
							bytes += portstat.bytesReceived();
						}
					}

					/*
					 * Aggregating traffic load of switches to controller and
					 * adding switch to the corresponding controller object/
					 */
					for (Controller controller : controllers) {
						// log.info(String.valueOf(mastershipStore.getMaster(d.id())));
						if (controller.nodeId.equals(mastershipStore.getMaster(d.id()))) {
							controller.controllerLoad += bytes;
							controller.addSwitch(d.id(), bytes);
						}
					}
				}

				// For testing...
				// Retrieving info of each controller and display in ONOS log
				for (Controller controller : controllers) {
					ArrayList<Switch> switches = controller.getSwitches();
					int numberOfSwitch = switches.size();
					String switchId[] = new String[numberOfSwitch];
					long switchLoad[] = new long[numberOfSwitch];
					int i = 0;
					for (Switch aSwitch : switches) {
						switchId[i] = aSwitch.deviceId.toString();
						switchLoad[i] = aSwitch.switchLoad;
						i++;
					}
					log.info("* " + controller.nodeId + " Load: " + controller.controllerLoad + " Switches: "
							+ Arrays.toString(switchId) + " Switch Load: " + Arrays.toString(switchLoad));
				}

				// Testing switch reassignment
				boolean test = true;
				for (Controller controller : controllers) {
					ArrayList<Switch> switches = controller.getSwitches();
					if ((switches.size() > 2) && (test) && (controller.nodeId != node3)) {
						// run once per schedule
						mastershipStore.setMaster(node3, switches.get(0).deviceId);
						log.info("# Switch reassigned.");
						test = false;
					}
				}

			}
		};
		// Timer delay 10 seconds; recommended 3 seconds(from ONOS documentation)
		timer.scheduleAtFixedRate(task, 0, 10000);

	}

	@Deactivate
	protected void deactivate() {
		timer.cancel();
		log.info("Stopped");
	}

}