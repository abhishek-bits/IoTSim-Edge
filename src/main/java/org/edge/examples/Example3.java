package org.edge.examples;


import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.VmScheduler;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisioner;
import org.cloudbus.cloudsim.provisioners.PeProvisioner;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;
import org.edge.core.edge.EdgeDataCenter;
import org.edge.core.edge.EdgeDataCenterBroker;
import org.edge.core.edge.EdgeDatacenterCharacteristics;
import org.edge.core.edge.EdgeDevice;
import org.edge.core.edge.MicroELement;
import org.edge.core.feature.EdgeType;
import org.edge.core.feature.Mobility;
import org.edge.core.feature.Mobility.MovingRange;
import org.edge.core.feature.operation.EdgeOperation;
import org.edge.core.iot.IoTDevice;
import org.edge.entity.ConfiguationEntity;
import org.edge.entity.ConfiguationEntity.BrokerEntity;
import org.edge.entity.ConfiguationEntity.BwProvisionerEntity;
import org.edge.entity.ConfiguationEntity.ConnectionEntity;
import org.edge.entity.ConfiguationEntity.EdgeDataCenterEntity;
import org.edge.entity.ConfiguationEntity.EdgeDatacenterCharacteristicsEntity;
import org.edge.entity.ConfiguationEntity.HostEntity;
import org.edge.entity.ConfiguationEntity.IotDeviceEntity;
import org.edge.entity.ConfiguationEntity.LogEntity;
import org.edge.entity.ConfiguationEntity.TraceEntity;
import org.edge.entity.ConfiguationEntity.MELEntities;
import org.edge.entity.ConfiguationEntity.MobilityEntity;
import org.edge.entity.ConfiguationEntity.NetworkModelEntity;
import org.edge.entity.ConfiguationEntity.PeEntity;
import org.edge.entity.ConfiguationEntity.RamProvisionerEntity;
import org.edge.entity.ConfiguationEntity.VmAllcationPolicyEntity;
import org.edge.entity.ConfiguationEntity.VmSchedulerEntity;
import org.edge.entity.ConnectionHeader;
import org.edge.entity.MicroElementTopologyEntity;
import org.edge.exception.MicroElementNotFoundException;
import org.edge.network.NetworkModel;
import org.edge.network.NetworkType;
import org.edge.protocol.AMQPProtocol;
import org.edge.protocol.CoAPProtocol;
import org.edge.protocol.CommunicationProtocol;
import org.edge.protocol.MQTTProtocol;
import org.edge.protocol.XMPPProtocol;
import org.edge.utils.Configuration;
import org.edge.utils.LogUtil;
import org.edge.utils.LogUtil.Level;

import com.google.gson.Gson;
import org.edge.utils.TraceUtil;

/**
 * this is another start up entrance, in which every single configuration was written in configuration file defined in resource directory.
 *
 * @author cody
 *
 */
@Configuration("configuration3.json")
public class Example3 {

	private static final String INDENT = "    ";

	private void buildupEMLConnection(List<MicroELement> vmList,	List<MELEntities> vmEntities) {

		for (MicroELement microELement : vmList) {

			TraceUtil.trace("T3: Example3.buildupEMLConnection() -> MicroElement.getId()");

			int id = microELement.getId();
			MicroElementTopologyEntity topologyEntity = null;

			inner :for ( MELEntities to : vmEntities) {

				TraceUtil.trace("T3: Example3.buildupEMLConnection() -> MELEntities.getMELTopology().getId()");

				if(to.getMELTopology().getId()==id) {

					TraceUtil.trace("T3: Example3.buildupEMLConnection() -> MELEntities.getMELTopology()");

					topologyEntity=to.getMELTopology();
					break inner;
				}
			}

			if(topologyEntity==null)
				throw new MicroElementNotFoundException("cannot find topology for MicroElement "+id);

			TraceUtil.trace("T3: Example3.buildupEMLConnection() -> MicroElementTopologyEntity.getUpLinkId()");

			//find uplink and bind it
			Integer upLinkId = topologyEntity.getUpLinkId();

			if(upLinkId!=null) {

				inner: for (MicroELement microELement2 : vmList) {

					TraceUtil.trace("T3: Example3.buildupEMLConnection() -> MicroElement.getId()");

					if(microELement2.getId()==upLinkId) {

						TraceUtil.trace("T3: Example3.buildupEMLConnection() -> MicroElement.setUpLink()");

						microELement.setUpLink(microELement2);
						break inner;
					}
				}

				TraceUtil.trace("T3: Example3.buildupEMLConnection() -> MicroElement.getUpLink()");

				if(microELement.getUpLink()==null)
					throw new MicroElementNotFoundException("cannot find uplink "+upLinkId+" for MicroElement "+id);
			}

			TraceUtil.trace("T3: Example3.buildupEMLConnection() -> MicroElementTopologyEntity.getDownLinkIds()");

			List<Integer> downLinkIds = topologyEntity.getDownLinkIds();
			downLinkIds.remove(null);
			List<MicroELement> downLink=new ArrayList<>();

			TraceUtil.trace("T3: Example3.buildupEMLConnection() -> MicroElement.setDownLink()");

			microELement.setDownLink(downLink);

			for (Integer downLinkID : downLinkIds) {
				//find the MEL having the same downLinkID
				//and set the MEL to  microELement

				boolean found=false;
				inner: for (MicroELement elm : vmList) {

					TraceUtil.trace("T3: Example3.buildupEMLConnection() -> MicroElement.getId()");

					if(elm.getId()==downLinkID) {

						if(downLink.contains(elm)) {
							throw new  IllegalAccessError("the EML: "+id+"cannot bind the same downlink twice");
						}
						downLink.add(elm);
						found=true;
						break inner;
					}else
					if(downLinkID==id) {
						throw new  IllegalAccessError("the EML "+id+"'s downlink cannot be itself");
					}

				}
				if(!found) {
					throw new  IllegalAccessError("cannot find the downlink: "+downLinkID+"for EML "+id);
				}
			}
		}
	}

	private static void printCloudletList(List<Cloudlet> list,List<MicroELement>melList, List<EdgeDataCenter> datacenters ) {
		int size = list.size();
		Cloudlet edgeLet;

		LogUtil.info("========== OUTPUT ==========");
		LogUtil.info("Edgelet ID" + INDENT +
				"MicroELement ID" + INDENT + "Execution Time" + INDENT
				+ "Start Time" + INDENT + "Finish Time" + INDENT + "Length" + INDENT + "Size");

		DecimalFormat dft = new DecimalFormat("0.00");
		DecimalFormat idft = new DecimalFormat("000");

		for (int i = 0; i < size; i++) {
			edgeLet = list.get(i);
			//Log.print(INDENT + idft.format(edgeLet.getCloudletId()) + INDENT + INDENT);

			if (edgeLet.getStatus() == Cloudlet.SUCCESS) {

				LogUtil.info(
						INDENT + idft.format(edgeLet.getCloudletId()) + INDENT + INDENT +
								edgeLet.getVmId() + INDENT
								+ INDENT + INDENT
								+ dft.format(edgeLet.getActualCPUTime()) + INDENT + INDENT
								+ INDENT + dft.format(edgeLet.getExecStartTime())
								+ INDENT + INDENT
								+ dft.format(edgeLet.getFinishTime())
								+ INDENT + INDENT +
								edgeLet.getCloudletLength()
								+ INDENT + INDENT +
								edgeLet.getCloudletFileSize()

				);
			}
		}


		edgeLet = list.get(list.size() - 1);
		edgeLet.getUtilizationModelRam().getUtilization(0);

		//LogUtil.info(edgeLet = list.get());
		System.out.println("HostList" + datacenters.get(0).getHostList().size());

		EdgeDevice e = (EdgeDevice) datacenters.get(0).getHostList().get(0);
		LogUtil.info(" EdgeDevice Consumed energy, " + " Time" + edgeLet.getFinishTime());
		//LogUtil.info(edgeLet = list.get());
		if (datacenters.get(0).getHostList().size() > 1) {
			e = (EdgeDevice) datacenters.get(0).getHostList().get(1);
			LogUtil.info(" EdgeDevice Consumed energy, " + " Time" + edgeLet.getFinishTime());

		}


		LogUtil.info("end-exp");


	}

	/**
	 * get network topology from configuration and set up whole network topology in classes
	 * @param conf
	 * @param edgeDevices
	 * @param brokerId
	 * @return
	 */
	private List<ConnectionHeader> setUpConnection(ConfiguationEntity conf, List<IoTDevice> edgeDevices, int brokerId) {

		TraceUtil.trace("T2: Example3.setUpConnection() -> ConfigurationEntity.getConnections()");

		List<ConnectionEntity> connections = conf.getConnections();
		List<ConnectionHeader>  header=new ArrayList<>();

		for (ConnectionEntity connectionEntity : connections) {

			TraceUtil.trace("T2: Example3.setUpConnection() -> ConnectionEntity.getAssigmentIoTId()");

			int assigmentIoTId = connectionEntity.getAssigmentIoTId();
			for (IoTDevice edgeDevice : edgeDevices) {

				TraceUtil.trace("T2: Example3.setUpConnection() -> IoTDevice.getAssigmentIoTId()");

				if(edgeDevice.getAssigmentIoTId()==assigmentIoTId) {

					TraceUtil.trace("T2: Example3.setUpConnection() -> ConnectionEntity.getVmId()");

					int vmId = connectionEntity.getVmId();

					header.add(new ConnectionHeader(vmId, edgeDevice.getId(), brokerId, edgeDevice.getNetworkModel().getCommunicationProtocol().getClass()));

				}
			}
		}
		return header;
	}

	/**
	 * inflate MicroELement parameters to MicroELement
	 * @param conf
	 * @param broker
	 * @return
	 */
	private List<MicroELement> createMEL(ConfiguationEntity conf, EdgeDataCenterBroker broker) {

		TraceUtil.trace("T3: Example3.createMEL() -> ConfiguationEntity.getMELEntities()");

		List<MELEntities> melEntities = conf.getMELEntities();
		List<MicroELement> vms=new ArrayList<>();

		for (MELEntities melEntity : melEntities) {

			TraceUtil.trace("T3: Example3.createMEL() -> ConfiguationEntity.MELEntities.getCloudletSchedulerClassName()");

			String cloudletSchedulerClassName = melEntity.getCloudletSchedulerClassName();
			CloudletScheduler cloudletScheduler;

			try {

				TraceUtil.trace("T3: Example3.createMEL() -> ConfiguationEntity.MELEntities.getEdgeOperationClass()");

				String edgeOperationStr = melEntity.getEdgeOperationClass();

				EdgeOperation edgeOperation = (EdgeOperation) Class.forName(edgeOperationStr).newInstance();
				cloudletScheduler = (CloudletScheduler) Class.forName(cloudletSchedulerClassName).newInstance();

				TraceUtil.trace("T3: Example3.createMEL() -> ConfiguationEntities.MELEntities.getDatasizeShrinkFactor()");

				float datasizeShrinkFactor = melEntity.getDatasizeShrinkFactor();

				TraceUtil.trace("T3: Example3.createMEL() -> ConfiguationEntity.MELEntities.getType()");

				String type = melEntity.getType();

				TraceUtil.trace("T3: Example3.createMEL() -> new MicroElement(...)");

				MicroELement microELement=new MicroELement(melEntity.getVmid()	, broker.getId(),melEntity.getMips(),
						melEntity.getPesNumber(),
						melEntity.getRam(),melEntity.getBw(),melEntity.getSize(), melEntity.getVmm(), cloudletScheduler,
						type,datasizeShrinkFactor
				);

				TraceUtil.trace("T3: Example3.createMEL() -> MicroElement.setEdgeOperation()");

				microELement.setEdgeOperation(edgeOperation);

				vms.add(microELement);

				TraceUtil.trace("T3: Example3.createMEL() -> ConfiguationEntity.MELEntities.getMELTopology()");

				MicroElementTopologyEntity melTopology = melEntity.getMELTopology();

				TraceUtil.trace("T3: Example3.createMEL() -> MicroElementTopologyEntity.getId()");

				melTopology.setId(microELement.getId());

			} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
		return vms;
	}

	/**
	 * create Iot Device from configuration
	 * @param conf
	 * @return
	 */
	private List<IoTDevice> createIoTDevice(ConfiguationEntity conf) {
		List<IotDeviceEntity> ioTDeviceEntities = conf.getIoTDeviceEntities();
		List<IoTDevice>  devices=new ArrayList<>();
		for (IotDeviceEntity iotDeviceEntity : ioTDeviceEntities) {
			List<IoTDevice> createIoTDevice = this.createIoTDevice(iotDeviceEntity);
			if (createIoTDevice.size()==0)
				return null;
			devices.addAll(createIoTDevice);
		}
		return devices;
	}
	/**
	 *
	 * create data center;
	 * @param conf
	 * @return
	 */
	private List<EdgeDataCenter> createDataCenter(ConfiguationEntity conf) {
		List<EdgeDataCenter> datacenters=new ArrayList<>();
		List<EdgeDataCenterEntity> edgeDatacenterEntities = conf.getEdgeDatacenter();

		for (EdgeDataCenterEntity edgeDataCenterEntity : edgeDatacenterEntities) {
			EdgeDataCenter createEdgeDatacenter = this.createEdgeDatacenter(edgeDataCenterEntity);
			datacenters.add(createEdgeDatacenter);
		}

		return datacenters;

	}

	private EdgeDataCenterBroker createBroker(ConfiguationEntity conf) {
		BrokerEntity brokerEntity = conf.getBroker();
		EdgeDataCenterBroker broker = this.createBroker(brokerEntity.getName());
		return broker;
	}

	/**
	 * Creates the datacenter.
	 * @param entity
	 *            the name
	 *
	 * @return the datacenter
	 */
	private EdgeDataCenter createEdgeDatacenter(EdgeDataCenterEntity entity) {

		List<HostEntity> hostListEntities = entity.getCharacteristics().getHostListEntities();
		List<EdgeDevice> hostList = new ArrayList<EdgeDevice>();
		try {
			for (HostEntity hostEntity : hostListEntities) {
				NetworkModelEntity networkModelEntity = hostEntity.getNetworkModel();
				NetworkModel networkModel = this.getNetworkModel(networkModelEntity);
				List<PeEntity> peEntities = hostEntity.getPeEntities();
				List<Pe> peList=this.getPeList(peEntities);
				RamProvisionerEntity ramProvisionerEntity = hostEntity.getRamProvisioner();
				Constructor<?> ramconstructor;

				ramconstructor = Class.forName(ramProvisionerEntity.getClassName()).getConstructor(int.class);

				RamProvisioner ramProvisioner=(RamProvisioner) ramconstructor.newInstance(ramProvisionerEntity.getRamSize());

				BwProvisionerEntity bwProvisionerEntity = hostEntity.getBwProvisioner();
				Constructor<?> bwconstructor = Class.forName(bwProvisionerEntity.getClassName()).getConstructor(double.class);
				BwProvisioner bwProvisioner=(BwProvisioner) bwconstructor.newInstance(bwProvisionerEntity.getBwSize());
				VmSchedulerEntity vmSchedulerEntity = hostEntity.getVmScheduler();
				String vmSchedulerClassName = vmSchedulerEntity.getClassName();
				VmScheduler vmScheduler = (VmScheduler) Class.forName(vmSchedulerClassName).getConstructor(List.class).newInstance(peList);
				MobilityEntity geo_location = hostEntity.getGeo_location();
				Mobility location=new Mobility(geo_location.getLocation());
				location.movable=geo_location.isMovable();
				location.signalRange=geo_location.getSignalRange();
				if(geo_location.isMovable()) {
					location.volecity=geo_location.getVolecity();
				}

				EdgeDevice edgeDevice = new EdgeDevice(hostEntity.getId(),ramProvisioner,bwProvisioner,hostEntity.getStorage(),peList,
						vmScheduler,this.getEdgeType(hostEntity.getEdgeType()),networkModel,
						hostEntity.getMax_IoTDevice_capacity(),hostEntity.getMax_battery_capacity(),
						hostEntity.getBattery_drainage_rate(),hostEntity.getCurrent_battery_capacity());
				edgeDevice.setMobility(location);

				hostList.add(edgeDevice);


			}
		} catch (Exception e) {
			e.printStackTrace();
		}


		// 2. A Machine contains one or more PEs or CPUs/Cores.
		// In this example, it will have only one core.

		// 4. Create Host with its id and list of PEs and add them to the list
		// of machines

		/*String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
		// devices by now
		EdgeDatacenterCharacteristics characteristics = new EdgeDatacenterCharacteristics(arch, os, vmm, hostList,
				time_zone, cost, costPerMem, costPerStorage, costPerBw, new Class[] { XMPPProtocol.class },
				new Class[] { TemperatureSensor.class }

		);*/

		EdgeDatacenterCharacteristicsEntity characteristicsEntity = entity.getCharacteristics();
		String architecture = characteristicsEntity.getArchitecture();
		String os = characteristicsEntity.getOs();
		String vmm = characteristicsEntity.getVmm();
		double timeZone = characteristicsEntity.getTimeZone();
		double costPerMem = characteristicsEntity.getCostPerMem();
		double cost = characteristicsEntity.getCost();

		double costPerStorage = characteristicsEntity.getCostPerStorage();
		double costPerBw = characteristicsEntity.getCostPerBw();
		LinkedList<Storage> storageList = new LinkedList<Storage>();
		List<String> ioTDeviceClassNameSupported = characteristicsEntity.getIoTDeviceClassNameSupported();
		Class[] ioTDeviceClassSupported=new Class[ioTDeviceClassNameSupported.size()];
		int i=0;
		for (String string : ioTDeviceClassNameSupported) {
			try {
				ioTDeviceClassSupported[i]=Class.forName(string);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}

			i++;
		}
		List<String> communicationNameSupported = characteristicsEntity.getCommunicationProtocolSupported();
		Class[] communicationClassSupported=new Class[communicationNameSupported.size()];
		i=0;
		for (String name : communicationNameSupported) {
			switch(name.toLowerCase()) {
				case "xmpp":
					communicationClassSupported[i]=XMPPProtocol.class;
					break;
				case "coap":
					communicationClassSupported[i]=CoAPProtocol.class;
					break;
				case "amqp":
					communicationClassSupported[i]=AMQPProtocol.class;
					break;
				case "mqtt":
					communicationClassSupported[i]=MQTTProtocol.class;
					break;
				default:
					System.out.println("the protocol " +name+" has not been supported yet!");
			}
			i++;
		}


		EdgeDatacenterCharacteristics characteristics = new EdgeDatacenterCharacteristics(architecture, os, vmm, hostList,
				timeZone, cost, costPerMem, costPerStorage, costPerBw,communicationClassSupported,
				ioTDeviceClassSupported);



		// Here are the steps needed to create a PowerDatacenter:
		// 1. We need to create a list to store
		// our machine


		VmAllcationPolicyEntity vmAllcationPolicyEntity = entity.getVmAllocationPolicy();
		String className = vmAllcationPolicyEntity.getClassName();

		// 6. Finally, we need to create a PowerDatacenter object.
		EdgeDataCenter datacenter = null;
		try {
			VmAllocationPolicy vmAllocationPolicy = (VmAllocationPolicy)Class.forName(className).getConstructor(List.class).newInstance(hostList);
			datacenter = new EdgeDataCenter(entity.getName(), characteristics,vmAllocationPolicy,
					storageList, entity.getSchedulingInterval());
		} catch (Exception e) {
			e.printStackTrace();
		}

		return datacenter;
	}

	private EdgeType getEdgeType(String edgeType) {
		String upperCase = edgeType.toUpperCase();
		EdgeType edgeType2=null;
		switch(upperCase) {
			case "RASPBERRY_PI":
				edgeType2=EdgeType.RASPBERRY_PI;
				break;

			case "SMART_ROUTER":
				edgeType2=EdgeType.SMART_ROUTER;

				break;

			case "UDOO_BOARD":
				edgeType2=EdgeType.UDOO_BOARD;

				break;

			case "MOBILE_PHONE":
				edgeType2=EdgeType.MOBILE_PHONE;

				break;

			default:
				System.out.println("the edgeDevice type "+edgeType+" has not been supported yet!");
				break;
		}

		return edgeType2;
	}

	private List<Pe> getPeList(List<PeEntity> peEntities) {
		List<Pe> peList = new ArrayList<Pe>();
		for (PeEntity peEntity : peEntities) {
			int mips = peEntity.getMips();
			String peProvisionerClassName = peEntity.getPeProvisionerClassName();
			try {
				Constructor<?> constructor = Class.forName(peProvisionerClassName).getConstructor(double.class);
				PeProvisioner newInstance = (PeProvisioner) constructor.newInstance(mips);
				peList.add(new Pe(peEntity.getId(), newInstance));
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}

		return peList;
	}

	private List<IoTDevice> createIoTDevice(IotDeviceEntity iotDeviceEntity) {
		List<IoTDevice> devices=new ArrayList<>();
		String ioTClassName = iotDeviceEntity.ioTClassName;
		NetworkModelEntity networkModelEntity = iotDeviceEntity.getNetworkModelEntity();
		// xmpp mqtt coap amqp
		NetworkModel networkModel = this.getNetworkModel(networkModelEntity);
		try {
			Class<?> clazz = Class.forName(ioTClassName);
			if (!IoTDevice.class.isAssignableFrom(clazz)) {
				System.out.println("this class is not correct type of ioT Device");
				return null;
			}
			Constructor<?> constructor = clazz.getConstructor(NetworkModel.class);
			int numberofEntity = iotDeviceEntity.getNumberofEntity();
			for (int i = 0; i <numberofEntity; i++) {

				IoTDevice newInstance = (IoTDevice) constructor.newInstance(networkModel);
				newInstance.setAssigmentIoTId(iotDeviceEntity.getAssignmentId());

				newInstance.setBatteryDrainageRate(iotDeviceEntity.getBattery_drainage_rate());
				newInstance.getBattery().setMaxCapacity(iotDeviceEntity.getMax_battery_capacity());
				newInstance.getBattery().setCurrentCapacity(iotDeviceEntity.getMax_battery_capacity());
				Mobility location=new Mobility(iotDeviceEntity.getMobilityEntity().getLocation());
				location.movable=iotDeviceEntity.getMobilityEntity().isMovable();
				if(iotDeviceEntity.getMobilityEntity().isMovable()) {
					location.range=new MovingRange(iotDeviceEntity.getMobilityEntity().getRange().beginX,
							iotDeviceEntity.getMobilityEntity().getRange().endX);
					location.signalRange=iotDeviceEntity.getMobilityEntity().getSignalRange();
					location.volecity=iotDeviceEntity.getMobilityEntity().getVolecity();
				}
				newInstance.setMobility(location);

				devices.add(newInstance);
			}



			return devices;

		} catch (ClassCastException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private NetworkModel getNetworkModel(NetworkModelEntity networkModelEntity) {
		String communicationProtocolName = networkModelEntity.getCommunicationProtocol();
		communicationProtocolName = communicationProtocolName.toLowerCase();
		CommunicationProtocol communicationProtocol = null;
		switch (communicationProtocolName) {
			case "xmpp":
				communicationProtocol = new XMPPProtocol();
				break;
			case "mqtt":
				communicationProtocol = new MQTTProtocol();
				break;
			case "coap":
				communicationProtocol = new CoAPProtocol();
				break;
			case "amqp":
				communicationProtocol = new AMQPProtocol();
				break;
			default:
				System.out.println("have not supported protocol " + communicationProtocol + " yet!");
				return null;
		}
		String networkTypeName = networkModelEntity.getNetworkType();
		networkTypeName = networkTypeName.toLowerCase();
		NetworkType networkType = null;
		switch (networkTypeName) {
			case "wifi":
				networkType = NetworkType.WIFI;
				break;
			case "wlan":
				networkType = NetworkType.WLAN;
				break;
			case "4g":
				networkType = NetworkType.FourG;
				break;
			case "3g":
				networkType = NetworkType.ThreeG;
				break;
			case "bluetooth":
				networkType = NetworkType.BLUETOOTH;
				break;
			case "lan":
				networkType = NetworkType.LAN;
				break;
			default:
				System.out.println("have not supported network type " + networkTypeName + " yet!");
				return null;
		}

		NetworkModel networkModel = new NetworkModel(networkType);
		networkModel.setCommunicationProtocol(communicationProtocol);
		return networkModel;
	}

	private EdgeDataCenterBroker createBroker(String brokerName) {
		EdgeDataCenterBroker broker = null;
		try {
			broker = new EdgeDataCenterBroker(brokerName);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return broker;
	}

	/**
	 * log initialization
	 *
	 * @param conf
	 */
	private void initLog(ConfiguationEntity conf) {
		LogEntity logEntity = conf.getLogEntity();
		boolean saveLogToFile = logEntity.isSaveLogToFile();
		if(saveLogToFile) {
			String logFilePath = logEntity.getLogFilePath();
			String logLevel = logEntity.getLogLevel();
			boolean append = logEntity.isAppend();
			LogUtil.initLog(Level.valueOf(logLevel.toUpperCase()), logFilePath, saveLogToFile,append);
		}

	}

	/**
	 * Trace Initialization
	 * @param conf
	 */
	private void initTrace(ConfiguationEntity conf) {
		TraceEntity traceEntity = conf.getTraceEntity();
		String traceFilePath = traceEntity.getTraceFilePath();
		TraceUtil.init(traceFilePath);
	}

	/**
	 * init CloudSim
	 * @param conf
	 */
	private void initCloudSim(ConfiguationEntity conf) {

		TraceUtil.trace("T1: Example3.initCloudSim() -> ConfiguationEntity.getNumUser()");

		int numUser = conf.getNumUser(); // number of cloud users

		TraceUtil.trace("T1: Example3.initCloudSim() -> Calender.getInstance()");

		Calendar calendar = Calendar.getInstance(); // Calendar whose fields have been initialized with the current date
		// and time.

		TraceUtil.trace("T1: Example3.initCloudSim() -> ConfiguationEntity.isTrace_flag()");

		//whether printing every single event in console
		boolean trace_flag = conf.isTrace_flag(); // trace events

		TraceUtil.trace("T1: Example3.initCloudSim() -> CloudSim.init()");

		CloudSim.init(numUser, calendar, trace_flag);
	}

	public void initFromConfiguation(ConfiguationEntity conf) {

		TraceUtil.trace("T1: Example3.initFromConfiguation() -> Example3.initCloudSim()");

		this.initCloudSim(conf);

		TraceUtil.trace("T1: Example3.initFromConfiguation() -> Example3.createBroker()");

		EdgeDataCenterBroker broker = this.createBroker(conf);

		TraceUtil.trace("T1: Example3.initFromConfiguation() -> Example3.createIoTDevice()");

		List<IoTDevice> edgeDevices=this.createIoTDevice(conf);

		TraceUtil.trace("T1: Example3.initFromConfiguation() -> Example3.createDataCenter()");

		List<EdgeDataCenter> datacenters=this.createDataCenter(conf);

		TraceUtil.trace("");

		/*
		 * T2
		 */

		TraceUtil.trace("T2: Example3.initFromConfiguation() -> Example3.setUpConnection()");

		List<ConnectionHeader>  header=this.setUpConnection(conf,edgeDevices,broker.getId());

		TraceUtil.trace("");

		/*
		 * T3
		 */

		TraceUtil.trace("T3: Example3.initFromConfiguation() -> Example3.createMEL()");

		List<MicroELement> melList=this.createMEL(conf,broker);

		TraceUtil.trace("T3: Example3.initFromConfiguation() -> Example3.buildupEMLConnection()");

		this.buildupEMLConnection(melList,conf.getMELEntities());

		TraceUtil.trace("");

		/*
		 * EdgeDataCenter ----- connect to IoT devices -----> MEL
		 */
		broker.submitVmList(melList);
		broker.submitConnection(header);

		this.initLog(conf);

		LogUtil.info("Start-exp");
		LogUtil.info("Number of IoT "+ INDENT +edgeDevices.size());
		LogUtil.info("Config of IoT Battery"+ INDENT +edgeDevices.get(0).getBattery().getCurrentCapacity());


		/*
		 * T4: IoTDevice.java
		 *
		 * T5: EdgeDataCenterBroker.java
		 */

		TraceUtil.trace("T4, T5: Example3.initFromConfiguration() -> CloudSim.startSimulation()");

		CloudSim.startSimulation();

		/*
		 * Finish printing the traces.
		 */
		TraceUtil.endTrace();

		/*
		 * Last Step in Fig. 9: Print Results
		 */

		List<Cloudlet> cloudletReceivedList = broker.getCloudletReceivedList();

		printCloudletList(cloudletReceivedList, melList,datacenters);

		LogUtil.simulationFinished();

	}

	/**
	 * read configuration file and to init the whole program
	 */
	public void init() {

		Configuration annotations = this.getClass().getAnnotation(Configuration.class);
		String value = annotations.value();

		if(value==null||value.isEmpty())
		{
			throw new IllegalArgumentException("configuration file required!");
		}

		InputStream resource = this.getClass().getClassLoader().getResourceAsStream(		value);
		Gson gson = new Gson();
		ConfiguationEntity conf = gson.fromJson(new InputStreamReader(resource), ConfiguationEntity.class);

		// initializing our own trace file
		this.initTrace(conf);

		TraceUtil.trace("Group 7");
		TraceUtil.trace("2021H1030070H" + INDENT + "ABHISHEK");
		TraceUtil.trace("2021H1030093H" + INDENT + "AVISHEK PAL");

		TraceUtil.trace("");

		TraceUtil.trace("T1: " + "Example3.init() -> Example3.initFromConfiguration()");

		this.initFromConfiguation(conf);

	}

	public static void main(String[] args) {
		Example3 startUp2 = new Example3();
		startUp2.init();
	}
}