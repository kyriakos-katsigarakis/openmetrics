package eu.openmetrics.kgg.service;

import java.util.Iterator;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import gr.tuc.ifc.IfcModel;
import gr.tuc.ifc4.IfcAreaMeasure;
import gr.tuc.ifc4.IfcBeam;
import gr.tuc.ifc4.IfcBuilding;
import gr.tuc.ifc4.IfcBuildingStorey;
import gr.tuc.ifc4.IfcColumn;
import gr.tuc.ifc4.IfcDoor;
import gr.tuc.ifc4.IfcElement;
import gr.tuc.ifc4.IfcIdentifier;
import gr.tuc.ifc4.IfcObjectDefinition;
import gr.tuc.ifc4.IfcOpeningElement;
import gr.tuc.ifc4.IfcProduct;
import gr.tuc.ifc4.IfcProject;
import gr.tuc.ifc4.IfcProperty;
import gr.tuc.ifc4.IfcPropertySet;
import gr.tuc.ifc4.IfcPropertySingleValue;
import gr.tuc.ifc4.IfcReal;
import gr.tuc.ifc4.IfcRelAggregates;
import gr.tuc.ifc4.IfcRelAssigns;
import gr.tuc.ifc4.IfcRelAssignsToGroup;
import gr.tuc.ifc4.IfcRelContainedInSpatialStructure;
import gr.tuc.ifc4.IfcRelDefinesByProperties;
import gr.tuc.ifc4.IfcRelFillsElement;
import gr.tuc.ifc4.IfcRelSpaceBoundary;
import gr.tuc.ifc4.IfcRelSpaceBoundary2ndLevel;
import gr.tuc.ifc4.IfcRelVoidsElement;
import gr.tuc.ifc4.IfcRoof;
import gr.tuc.ifc4.IfcSite;
import gr.tuc.ifc4.IfcSlab;
import gr.tuc.ifc4.IfcSpace;
import gr.tuc.ifc4.IfcSpaceBoundarySelect;
import gr.tuc.ifc4.IfcSpatialElement;
import gr.tuc.ifc4.IfcUnitaryControlElement;
import gr.tuc.ifc4.IfcUnitaryControlElementTypeEnum;
import gr.tuc.ifc4.IfcUnitaryEquipment;
import gr.tuc.ifc4.IfcUnitaryEquipmentTypeEnum;
import gr.tuc.ifc4.IfcValue;
import gr.tuc.ifc4.IfcVolumeMeasure;
import gr.tuc.ifc4.IfcWall;
import gr.tuc.ifc4.IfcWallStandardCase;
import gr.tuc.ifc4.IfcWindow;
import gr.tuc.ifc4.IfcZone;

@Service
public class Converter {

	private static Logger log = LoggerFactory.getLogger(Converter.class);
	
	private Model rdfModel;
	
	public Converter() {
		log.info("Knowledge Graph Generator");
	}

	public Model convert(IfcModel ifcModel) {
		rdfModel = ModelFactory.createDefaultModel();
		rdfModel.setNsPrefix("owl", OWL.getURI());
		rdfModel.setNsPrefix("rdf", RDF.getURI());
		rdfModel.setNsPrefix("rdfs", RDFS.getURI());
		rdfModel.setNsPrefix("xsd", XSD.getURI());
		rdfModel.setNsPrefix("schema", "http://schema.org#");
		rdfModel.setNsPrefix("om", "http://openmetrics.eu/openmetrics#");
		rdfModel.setNsPrefix("brick", "https://brickschema.org/schema/1.1/Brick#");
		rdfModel.setNsPrefix("props", "https://w3id.org/props#");
		rdfModel.setNsPrefix("saref", "http://w3id.irg/saref#");
		Iterator<IfcProject> projectIterator = ifcModel.getAllE(IfcProject.class).iterator();
		if(projectIterator.hasNext()) {
			IfcProject ifcProject = projectIterator.next();
			parseProject(ifcProject);
		}
		return rdfModel;
	}
	
	private void parseProject(IfcProject ifcProject) {
		Iterator<IfcRelAggregates> projectRelAggregatesIterator = ifcProject.getIsDecomposedBy().iterator();
		while(projectRelAggregatesIterator.hasNext()) {
			IfcRelAggregates projectRelAggregates = projectRelAggregatesIterator.next();
			List<IfcObjectDefinition> projectRelatedObjectList = projectRelAggregates.getRelatedObjects();
			for(IfcObjectDefinition projectRelatedObject : projectRelatedObjectList) {
				if(projectRelatedObject instanceof IfcSite) {
					IfcSite ifcSite = (IfcSite) projectRelatedObject;
					Resource resSite =  rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Site_" + ifcSite.getExpressId());
					//resSite.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Site"));
					resSite.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Site"));
					resSite.addProperty(RDFS.label, ResourceFactory.createStringLiteral(   ifcSite.getName() != null ? ifcSite.getName().getValue() : "Undefined"  ));
					resSite.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( ifcSite.getGlobalId().getValue() ));
					Iterator<IfcRelAggregates> siteRelAggregatesIterator = ifcSite.getIsDecomposedBy().iterator();
					while(siteRelAggregatesIterator.hasNext()) {
						IfcRelAggregates siteRelAggregates = siteRelAggregatesIterator.next();
						List<IfcObjectDefinition> siteRelatedObjectList = siteRelAggregates.getRelatedObjects();
						for(IfcObjectDefinition siteRelatedObject : siteRelatedObjectList) {
							if(siteRelatedObject instanceof IfcBuilding) {
								IfcBuilding building = (IfcBuilding) siteRelatedObject;
								parseBuilding(building, resSite);
							}else {
								log.warn("unsupported case");
							}
						}
					}
				}else if (projectRelatedObject instanceof IfcBuilding) {
					IfcBuilding building = (IfcBuilding) projectRelatedObject;
					parseBuilding(building, null);					
				}else {
					log.warn("usupported case");
				}
			}
		}
	}
	
	private void parseBuilding(IfcBuilding building, Resource resSite) {
		Resource resBuilding = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Building_" + building.getExpressId());
		resBuilding.addLiteral(RDFS.label, ResourceFactory.createStringLiteral(  building.getName() != null ? building.getName().getValue() : "Undefined"  ));
		//resBuilding.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Building"));
		resBuilding.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Building"));
		resBuilding.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( building.getGlobalId().getValue() ));
		// update parent
		if(resSite != null) {
			resSite.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasLocation"), resBuilding);
			//resSite.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "hasBuilding"), resBuilding);				
		}
		// using containedInSpatialStructure
		Iterator<IfcRelContainedInSpatialStructure> buildingContainedInSpatialStructureIterator = building.getContainsElements().iterator();
		while(buildingContainedInSpatialStructureIterator.hasNext()) {
			IfcRelContainedInSpatialStructure relContainedInSpatialStructure = buildingContainedInSpatialStructureIterator.next();
			for(IfcProduct product : relContainedInSpatialStructure.getRelatedElements()) {
				parseProduct(product, resBuilding);
			}
		}
		// using decomposedBy
		Iterator<IfcRelAggregates> buildingRelAggregatesIterator = building.getIsDecomposedBy().iterator();
		while(buildingRelAggregatesIterator.hasNext()) {
			IfcRelAggregates buildingRelAggregates = buildingRelAggregatesIterator.next();
			List<IfcObjectDefinition> buildingRelatedObjectList = buildingRelAggregates.getRelatedObjects();
			for(IfcObjectDefinition builidngRelatedObject : buildingRelatedObjectList) {
				if(builidngRelatedObject instanceof IfcBuildingStorey) {
					IfcBuildingStorey buildingStorey = (IfcBuildingStorey) builidngRelatedObject;
					//
					Resource resStorey = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "BuildingStorey_" + buildingStorey.getExpressId());
					resStorey.addLiteral(RDFS.label, ResourceFactory.createStringLiteral(  buildingStorey.getName() != null ? buildingStorey.getName().getValue() : "Undefined"  ));
					//resStorey.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Storey"));
					resStorey.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Storey"));
					resStorey.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasLocation"), resBuilding);
					resStorey.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( buildingStorey.getGlobalId().getValue() ));
					// parent resource
					//resBuilding.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "hasStorey"), resStorey);
					//resBuilding.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasPart"), resStorey);
					// using decomposedBy
					Iterator<IfcRelAggregates> buildingStoreyRelAggregatesIterator = buildingStorey.getIsDecomposedBy().iterator(); 
					while(buildingStoreyRelAggregatesIterator.hasNext()) {
						IfcRelAggregates buildingStoreyRelAggregates = buildingStoreyRelAggregatesIterator.next();
						List<IfcObjectDefinition> buildingStoreyRelatedObjectList = buildingStoreyRelAggregates.getRelatedObjects();
						for(IfcObjectDefinition buildingStoreyRelatedObject : buildingStoreyRelatedObjectList) {
							if(buildingStoreyRelatedObject instanceof IfcProduct) {
								IfcProduct product = (IfcProduct) buildingStoreyRelatedObject;					
								parseProduct(product, resStorey);
							}else {
								log.warn("unsupported case");
							}
						}
					}
					// using containedInSpatialStructure
					Iterator<IfcRelContainedInSpatialStructure> buildingStoreyContainedInSpatialStructureIterator = buildingStorey.getContainsElements().iterator();
					while(buildingStoreyContainedInSpatialStructureIterator.hasNext()) {
						IfcRelContainedInSpatialStructure relContainedInSpatialStructure = buildingStoreyContainedInSpatialStructureIterator.next();
						for(IfcProduct product : relContainedInSpatialStructure.getRelatedElements()) {
							parseProduct(product, resStorey);
						}
					}					
				}else {
					log.warn("unsupported case");
				}
			}
		}
	}
	
	private void parseProduct(IfcProduct product, Resource resParent) {
		
		if(product instanceof IfcSpace) {
			IfcSpace ifcSpace = (IfcSpace) product;
			Resource resSpace = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Space_" + ifcSpace.getExpressId());
			resSpace.addLiteral(RDFS.label, ResourceFactory.createStringLiteral(  ifcSpace.getName() != null ? ifcSpace.getName().getValue() : "Undefined"  ));
			resSpace.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Space"));
			resSpace.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasLocation"), resParent);
			resSpace.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( ifcSpace.getGlobalId().getValue() ));
			// properties
			parsePsets(ifcSpace, resSpace);
			//
			// zones
			Iterator<IfcRelAssigns> spaceAssignmentIterator = ifcSpace.getHasAssignments().iterator();
			while(spaceAssignmentIterator.hasNext()) {
				IfcRelAssigns relAssigns = spaceAssignmentIterator.next();
				if(relAssigns instanceof IfcRelAssignsToGroup) {
					IfcRelAssignsToGroup relAssignsToGroup = (IfcRelAssignsToGroup) relAssigns;
					if(relAssignsToGroup.getRelatingGroup() instanceof IfcZone) {
						IfcZone ifcZone = (IfcZone) relAssignsToGroup.getRelatingGroup();
						Resource resZone = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Zone_" + ifcZone.getExpressId());
						resZone.addLiteral(RDFS.label, ResourceFactory.createStringLiteral(  ifcZone.getName() != null ? ifcZone.getName().getValue() : "Undefined"  ));
						resZone.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Zone"));
						resZone.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasPart"), resSpace);
					}else {
						log.warn("unsupported case");
					}
				}else {
					log.warn("unsupported case");
				}
			}
			// products included in the space
			Iterator<IfcRelContainedInSpatialStructure> buildingContainedInSpatialStructureIterator = ifcSpace.getContainsElements().iterator();
			while(buildingContainedInSpatialStructureIterator.hasNext()) {
				IfcRelContainedInSpatialStructure relContainedInSpatialStructure = buildingContainedInSpatialStructureIterator.next();
				for(IfcProduct spaceProduct : relContainedInSpatialStructure.getRelatedElements()) {		
					parseProduct(spaceProduct, resSpace);
				}
			}	
		}else if(product instanceof IfcUnitaryEquipment) {
			IfcUnitaryEquipment ifcUnitaryEquipment = (IfcUnitaryEquipment) product;
			if(ifcUnitaryEquipment.getPredefinedType().equals(IfcUnitaryEquipmentTypeEnum.SPLITSYSTEM)) {
				Resource resSplitSystem = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Element_" + ifcUnitaryEquipment.getExpressId() );
				resSplitSystem.addLiteral(RDFS.label, ResourceFactory.createStringLiteral( ifcUnitaryEquipment.getName() != null ? ifcUnitaryEquipment.getName().getValue() : "Undefined" ));
				resSplitSystem.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Terminal_Unit"));
				resSplitSystem.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("saref") + "HVAC"));
				resSplitSystem.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( ifcUnitaryEquipment.getGlobalId().getValue() ));				
				resSplitSystem.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasLocation"), resParent);
				Iterator<IfcRelContainedInSpatialStructure> containedInSpatialStructureIterator = ifcUnitaryEquipment.getContainedInStructure().iterator();
				while(containedInSpatialStructureIterator.hasNext()) {
					IfcRelContainedInSpatialStructure relContainedInSpatialStructure = containedInSpatialStructureIterator.next();		
					IfcSpatialElement spatialElement = relContainedInSpatialStructure.getRelatingStructure();	
					if(spatialElement instanceof IfcSpace) {
						IfcSpace ifcSpace = (IfcSpace) spatialElement;
						Iterator<IfcRelAssigns> relAssignIterator = ifcSpace.getHasAssignments().iterator();
						while(relAssignIterator.hasNext()) {		
							IfcRelAssigns relAssigns = relAssignIterator.next();
							if(relAssigns instanceof IfcRelAssignsToGroup) {
								IfcRelAssignsToGroup relAssignsToGroup = (IfcRelAssignsToGroup) relAssigns;
								if(relAssignsToGroup.getRelatingGroup() instanceof IfcZone) {
									IfcZone ifcZone = (IfcZone) relAssignsToGroup.getRelatingGroup();
									Resource resZone = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Zone_" + ifcZone.getExpressId());
									resSplitSystem.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "feeds"), resZone);
								}
							}
						}
					}else {
						log.warn("unsupported case");
					}
				}
				// add psets
				parsePsets(ifcUnitaryEquipment, resSplitSystem);
									
			}else {
				log.warn("unsupported case");
			}
			
		}else if(product instanceof IfcUnitaryControlElement) {
			IfcUnitaryControlElement ifcUnitaryControlElement = (IfcUnitaryControlElement) product;		
			if(ifcUnitaryControlElement.getPredefinedType().equals(IfcUnitaryControlElementTypeEnum.THERMOSTAT)) {
				Resource resControlPanel = rdfModel.createResource( rdfModel.getNsPrefixURI("om") + "Element_" + ifcUnitaryControlElement.getExpressId() );
				resControlPanel.addLiteral(RDFS.label, ResourceFactory.createStringLiteral( ifcUnitaryControlElement.getName() != null ? ifcUnitaryControlElement.getName().getValue() : "Undefined" ));
				resControlPanel.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("brick") + "Thermostat"));
				resControlPanel.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("saref") + "Device"));
				resControlPanel.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( ifcUnitaryControlElement.getGlobalId().getValue() ));
				resControlPanel.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("brick") + "hasLocation"), resParent);
				// add psets
				parsePsets(ifcUnitaryControlElement, resControlPanel);
			}else {
				log.warn("unsupported case11");
			}
		}
	}

	public void parseOpening(IfcOpeningElement opening, Resource resParent) {
		Iterator<IfcRelFillsElement> relFillsElementIterator = opening.getHasFillings().iterator();
		while(relFillsElementIterator.hasNext()) {
			IfcRelFillsElement relFillsElement = relFillsElementIterator.next();
			if(relFillsElement.getRelatedBuildingElement() instanceof IfcDoor) {
				IfcDoor ifcDoor = (IfcDoor) relFillsElement.getRelatedBuildingElement();
				Resource resDoor = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Element_" + ifcDoor.getExpressId());
				resDoor.addLiteral(RDFS.label, ResourceFactory.createStringLiteral(  ifcDoor.getName() != null ? ifcDoor.getName().getValue() : "Undefined"  ));
				resDoor.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Element"));
				resDoor.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("beo") + "Door"));	
				resDoor.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( ifcDoor.getGlobalId().getValue() ));
				parsePsets(ifcDoor, resDoor);
				// upd parent
				resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "hasSubElement"), resDoor);													
			}else if(relFillsElement.getRelatedBuildingElement() instanceof IfcWindow) {				
				IfcWindow ifcWindow = (IfcWindow) relFillsElement.getRelatedBuildingElement();
				Resource resWindow = rdfModel.createResource(rdfModel.getNsPrefixURI("om") + "Element_" + ifcWindow.getExpressId());
				resWindow.addLiteral(RDFS.label, ResourceFactory.createStringLiteral(  ifcWindow.getName() != null ? ifcWindow.getName().getValue() : "Undefined"  ));
				resWindow.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("bot") + "Element"));
				resWindow.addProperty(RDF.type, ResourceFactory.createResource( rdfModel.getNsPrefixURI("beo") + "Window"));
				resWindow.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCompressedGuid"), ResourceFactory.createStringLiteral( ifcWindow.getGlobalId().getValue() ));
				parsePsets(ifcWindow, resWindow);
				// upd parent
				resParent.addProperty(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("bot") + "hasSubElement"), resWindow);													
			}else {
				log.warn("unsupported case");
			}
		}
	}

	void parsePsets(IfcProduct ifcProduct, Resource resProduct) {
		Iterator<IfcRelDefinesByProperties> windowRelDefinesByProperties = ifcProduct.getIsDefinedBy().iterator();
		while(windowRelDefinesByProperties.hasNext()) {
			IfcRelDefinesByProperties relDefinesByProperties = windowRelDefinesByProperties.next();
			if(relDefinesByProperties.getRelatingPropertyDefinition() instanceof IfcPropertySet) {
				IfcPropertySet propertySet = (IfcPropertySet) relDefinesByProperties.getRelatingPropertyDefinition();
				Iterator<IfcProperty> propertiesIterator = propertySet.getHasProperties().iterator();
				while(propertiesIterator.hasNext()) {
					IfcProperty ifcProperty = propertiesIterator.next();
					if(ifcProperty instanceof IfcPropertySingleValue) {
						IfcPropertySingleValue propertySignleValue = (IfcPropertySingleValue) ifcProperty;
						if(propertySignleValue.getName().getValue().equalsIgnoreCase("Ufactor")) {
							IfcValue ifcValue = propertySignleValue.getNominalValue();
							if(ifcValue instanceof IfcReal) {
								IfcReal ifcMeasure = (IfcReal) ifcValue;
								resProduct.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "heatTransferCoefficientU"),  ResourceFactory.createTypedLiteral(ifcMeasure.getValue()));
							}									
						}else if(propertySignleValue.getName().getValue().equalsIgnoreCase("SolarHeatGainCoefficient")) {
							IfcValue ifcValue = propertySignleValue.getNominalValue();
							if(ifcValue instanceof IfcReal) {
								IfcReal ifcMeasure = (IfcReal) ifcValue;
								resProduct.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "solarHeatGainCoefficient"),  ResourceFactory.createTypedLiteral(ifcMeasure.getValue()));
							}
						}else if(propertySignleValue.getName().getValue().equalsIgnoreCase("VisibleTransmittance")) {
							IfcValue ifcValue = propertySignleValue.getNominalValue();
							if(ifcValue instanceof IfcReal) {
								IfcReal ifcMeasure = (IfcReal) ifcValue;
								resProduct.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "visualLightTransmittance"),  ResourceFactory.createTypedLiteral(ifcMeasure.getValue()));
							}
						}else if(propertySignleValue.getName().getValue().equalsIgnoreCase("Area")) {
							IfcValue ifcValue = propertySignleValue.getNominalValue();
							if(ifcValue instanceof IfcAreaMeasure) {
								IfcAreaMeasure ifcMeasure = (IfcAreaMeasure) ifcValue;
								resProduct.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasArea"),  ResourceFactory.createTypedLiteral(ifcMeasure.getValue()));
							}
						}else if(propertySignleValue.getName().getValue().equalsIgnoreCase("Volume")) {
							IfcValue ifcValue = propertySignleValue.getNominalValue();
							if(ifcValue instanceof IfcVolumeMeasure) {
								IfcVolumeMeasure ifcMeasure = (IfcVolumeMeasure) ifcValue;
								resProduct.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasVolume"),  ResourceFactory.createTypedLiteral(ifcMeasure.getValue()));
							}
						}else if(propertySignleValue.getName().getValue().equalsIgnoreCase("SerialNumber")) {
							IfcValue ifcValue = propertySignleValue.getNominalValue();
							if(ifcValue instanceof IfcIdentifier) {
								IfcIdentifier ifcIdentifier = (IfcIdentifier) ifcValue;
								resProduct.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasSerialNumber"),  ResourceFactory.createStringLiteral(ifcIdentifier.getValue()));
							}
						}else if(propertySignleValue.getName().getValue().equalsIgnoreCase("COP")) {
							IfcValue ifcValue = propertySignleValue.getNominalValue();
							if(ifcValue instanceof IfcReal) {
								IfcReal ifcMeasure = (IfcReal) ifcValue;
								resProduct.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasCOP"),  ResourceFactory.createTypedLiteral(ifcMeasure.getValue()));
							}
						}else if(propertySignleValue.getName().getValue().equalsIgnoreCase("EER")) {
							IfcValue ifcValue = propertySignleValue.getNominalValue();
							if(ifcValue instanceof IfcReal) {
								IfcReal ifcMeasure = (IfcReal) ifcValue;
								resProduct.addLiteral(ResourceFactory.createProperty(rdfModel.getNsPrefixURI("props") + "hasEER"),  ResourceFactory.createTypedLiteral(ifcMeasure.getValue()));
							}
						}
					}
				}
			}else {
				log.warn("unsupported case");
			}
		}
	}
}