package com.blangdon.flume.rabbitmq;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.MessageProperties;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudera.flume.conf.Context;
import com.cloudera.flume.conf.SinkFactory.SinkBuilder;
import com.cloudera.flume.core.Event;
import com.cloudera.flume.core.EventSink;
import com.cloudera.util.Pair;
import com.google.common.base.Preconditions;

public class RabbitMQSink extends EventSink.Base{
    private static final Logger LOG = LoggerFactory.getLogger(RabbitMQSink.class);


    private String queue = null;
    private String username = null;
    private String password = null;
    private Address[] servers = null;
    private Connection connection = null;
    private Channel channel = null;

    public RabbitMQSink( String queue, String username, String password, String servers ){
	this.queue = queue;
	this.username = username;
	this.password = password;
	
	String[] servList = servers.split(" ");
	this.servers = new Address[servList.length];

	for( int i = 0; i < servList.length; ++i ){
	    Address next = Address.parseAddress(servList[i]);
	    LOG.info("Address: " + next.toString() + " Added To List");
	    this.servers[i] = next;
	}
    }


    @Override
	public void open() throws IOException{
	//open connection to a server here

	Collections.shuffle(Arrays.asList(this.servers));

	ConnectionFactory cf = new ConnectionFactory();
	if( this.username != null ){
	    cf.setUsername(this.username);
	}

	if( this.password != null ){
	    cf.setPassword(this.password);
	}

	cf.setVirtualHost("/");

	try{
	    this.connection = cf.newConnection(this.servers);
	    this.channel = this.connection.createChannel();
	    this.channel.queueDeclare(this.queue, true, false, false, null);
	    
	    String host = this.connection.getAddress().toString();
	    
	    LOG.info("Connected To Server: " + host);
	}catch( Exception ex ){
	    LOG.warn("Could Not Connect To RabbitMQ Server");
	    throw ex;
	}
    }

    @Override
	public void append(Event e) throws IOException{
	//publish event

	if( this.channel != null ){
	    byte[] message = e.getBody();
	    this.channel.basicPublish("", this.queue, MessageProperties.TEXT_PLAIN, message);
	}else{
	    this.open();
	}
    }

    @Override
	public void close() throws IOException{
	
	if( this.channel != null ){
	    this.channel.close();
	}

	if( this.connection != null ){
	    this.connection.close();
	}

    }


    public static SinkBuilder builder(){
	return new SinkBuilder(){
	    @Override
		public EventSink build(Context context, String... argv){
		Preconditions.checkArgument(argv.length > 1,
					    "usage: rabbitMQSink(queueName, serverList, [username, password])");
		
		String queueName = argv[0];
		String servers = argv[1];
		
		String username = null;
		if( argv.length > 2 ){
		    username = argv[2];
		}
		
		String password = null;
		if( argv.length > 3 ){
		    password = argv[3];
		}
		
		return new RabbitMQSink(queueName, username, password, servers);
	    }
	};
   }


    public static List<Pair<String, SinkBuilder>> getSinkBuilders(){
	List<Pair<String, SinkBuilder>> builders = 
	    new ArrayList<Pair<String, SinkBuilder>>();
	builders.add( new Pair<String, SinkBuilder>("rabbitMQSink", builder()));
	return builders;
    }

}