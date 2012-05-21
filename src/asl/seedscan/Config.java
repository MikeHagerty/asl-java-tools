/*
 * Copyright 2011, United States Geological Survey or
 * third-party contributors as indicated by the @author tags.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/  >.
 *
 */
package asl.seedscan;

import java.io.BufferedInputStream;
import java.io.Console;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collection;
import java.util.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.XMLConstants;

import org.xml.sax.SAXException;

import asl.seedscan.config.ConfigT;

public class Config
{
    private static final Logger logger = Logger.getLogger("asl.seedscan.Config");

    private ConfigT config = null;
    private Schema  schema = null;

    public Config(File configFile, Collection<File> schemaFiles)
    {
        schema = makeSchema(schemaFiles);
        config = parseConfig(configFile, schema);
    }

    private Schema makeSchema(Collection<File> files) 
    {
        Schema schema = null;
        StreamSource[] sources = new StreamSource[files.size()];

        int i = 0;
        for (File file: files) {
            sources[i] = new StreamSource(file);
            i++;
        }

        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            schema = factory.newSchema(sources);
        } catch (SAXException ex) {
            String message = "Could not generate schema from supplied files: " + ex.toString();
            logger.severe(message);
            throw new RuntimeException(message);
        }

        return schema;
    }

    private ConfigT parseConfig(File configFile, Schema schema)
    {
        ConfigT cfg = null;

        try {
            JAXBContext context = JAXBContext.newInstance("asl.seedscan.config");
            Unmarshaller unmarshaller = context.createUnmarshaller();
            unmarshaller.setSchema(schema);
            InputStream stream = new BufferedInputStream(
                                 new DataInputStream(
                                 new FileInputStream(configFile)));
            JAXBElement<ConfigT> cfgRoot = (JAXBElement<ConfigT>)unmarshaller.unmarshal(stream);
            cfg = cfgRoot.getValue();
        } catch (FileNotFoundException ex) {
            String message = "Could not locate config file: " + ex.toString();
            logger.severe(message);
            throw new RuntimeException(message);
        } catch (JAXBException ex) {
            String message = "Could not unmarshal config file: " + ex.toString();
            logger.severe(message);
            throw new RuntimeException(message);
        }

        return cfg;
    }

}
