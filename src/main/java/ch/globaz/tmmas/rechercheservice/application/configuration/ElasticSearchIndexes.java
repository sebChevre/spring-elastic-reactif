package ch.globaz.tmmas.rechercheservice.application.configuration;

public enum ElasticSearchIndexes {

	PERSONNES("personne","personne");

	String index;
	String type;

	ElasticSearchIndexes(String index, String type){
		this.index = index;
		this.type = type;
	}

	public String index(){
		return this.index;
	}

	public String type(){
		return this.type;
	}
}
