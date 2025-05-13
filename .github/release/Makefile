check-links:
	../scripts/check-links.sh ../test/cd-agent/projects/example.yaml
	../scripts/check-links.sh ../../clinical-domain-agent/application.yaml
	../scripts/check-links.sh ../../trust-center-agent/application.yaml
	../scripts/check-links.sh ../test/rd-agent/projects/example.yaml
	../scripts/check-links.sh ../../research-domain-agent/application.yaml

cd-agent:
	cp -r ../test/cd-agent .
	sed -i '/# CI_ONLY/d' ./cd-agent/compose.yaml
	grep ":local" ./cd-agent/compose.yaml
	sed -i "s/:local/$${COMMIT_TAG:+:$${COMMIT_TAG#v}}@$${CD_AGENT_DIGEST}/g" ./cd-agent/compose.yaml
	mkdir -p ./cd-agent/projects/example
	cp -r ../test/deidentifhir ./cd-agent/projects/example
	find ./cd-agent/projects/example/deidentifhir \
		-type f \( -name "TransportToRD.profile" -o -name "TransportToRD.conf" \) -delete
	cp ../../clinical-domain-agent/application.yaml ./cd-agent/
	tar -czf cd-agent.tar.gz ./cd-agent/

tc-agent:
	cp -r ../test/tc-agent .
	sed -i '/# CI_ONLY/d' ./tc-agent/compose.yaml
	grep ":local" ./tc-agent/compose.yaml
	sed -i "s/:local/$${COMMIT_TAG:+:$${COMMIT_TAG#v}}@$${TC_AGENT_DIGEST}/g" ./tc-agent/compose.yaml
	cp ../../trust-center-agent/application.yaml ./tc-agent/
	tar -czf tc-agent.tar.gz ./tc-agent/

rd-agent:
	cp -r ../test/rd-agent .
	sed -i '/# CI_ONLY/d' ./rd-agent/compose.yaml
	grep ":local" ./rd-agent/compose.yaml
	sed -i "s/:local/$${COMMIT_TAG:+:$${COMMIT_TAG#v}}@$${RD_AGENT_DIGEST}/g" ./rd-agent/compose.yaml
	mkdir -p ./rd-agent/projects/example
	cp -r ../test/deidentifhir ./rd-agent/projects/example
	find ./rd-agent/projects/example/deidentifhir \
		-type f \( -name "CDtoTransport.profile" -o -name "CDtoTransport.conf" \) -delete
	find ./rd-agent/projects/example/deidentifhir \
		-type f \( -name "IDScraper.profile" -o -name "IDScraper.conf" \) -delete
	cp ../../research-domain-agent/application.yaml ./rd-agent/
	tar -czf rd-agent.tar.gz ./rd-agent/

clean:
	rm -Rf *-agent*

.PHONY: cd-agent tc-agent rd-agent clean
