package edu.cmu.cs.bungee.populate;

public class DC extends Decoder {

	private static final DC SELF = new DC();

	public static DC getInstance() {
		// if (self == null) {
		// self = new DC();
		// }
		return SELF;
	}

	DC() {
		addCode("identifier", FunctionalAttribute.getAttribute("URI", true));
		addCode("title", Attribute.getAttribute("Title"));
		addCode("description", Attribute.getAttribute("Description"));
		addCode("publisher", Facet.getGenericFacet("Publisher"));
		addCode("format", Facet.getGenericFacet("Format"));
		addCode("rights", Facet.getGenericFacet("Rights"));
		addCode("type", Facet.getGenericFacet("Type"));
		addCode("date", Facet.getDateFacet("Date"));
		addCode("subject", Facet.getSubjectFacet("Subject"));
		addCode("language", Facet.getGenericFacet("Language"));
		addCode("creator", Facet.getGenericFacet("Creator"));
		addCode("source", Facet.getPreparsedFacet("Source"));
		addCode("contributor", Facet.getGenericFacet("Contributor"));
		addCode("coverage", Facet.getPlaceFacet("Coverage"));
	}

}