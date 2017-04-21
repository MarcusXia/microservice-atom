package com.ewolff.microservice.order.logic;

import static org.junit.Assert.*;

import java.net.URI;
import java.util.stream.StreamSupport;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriTemplate;

import com.ewolff.microservice.order.OrderApp;
import com.ewolff.microservice.order.customer.Customer;
import com.ewolff.microservice.order.customer.CustomerRepository;
import com.ewolff.microservice.order.item.Item;
import com.ewolff.microservice.order.item.ItemRepository;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = OrderApp.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class OrderWebIntegrationTest {

	private RestTemplate restTemplate = new RestTemplate();

	@LocalServerPort
	private long serverPort;

	@Autowired
	private ItemRepository itemRepository;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private OrderRepository orderRepository;

	private Item item;

	private Customer customer;

	@Before
	public void setup() {
		item = itemRepository.findAll().iterator().next();
		customer = new Customer("RZA", "GZA", "rza@wutang.com", "Chamber", "Shaolin");
		customer = customerRepository.save(customer);
	}

	@Test
	public void IsOrderListReturned() {
		try {
			Iterable<Order> orders = orderRepository.findAll();
			assertTrue(StreamSupport.stream(orders.spliterator(), false)
					.noneMatch(o -> ((o.getCustomer() != null) && (o.getCustomer().equals(customer)))));
			ResponseEntity<String> resultEntity = restTemplate.getForEntity(orderURL(), String.class);
			assertTrue(resultEntity.getStatusCode().is2xxSuccessful());
			String orderList = resultEntity.getBody();
			assertFalse(orderList.contains("RZA"));
			Order order = new Order(customer);
			order.addLine(42, item);
			orderRepository.save(order);
			orderList = restTemplate.getForObject(orderURL(), String.class);
			assertTrue(orderList.contains("Eberhard"));
		} finally {
			orderRepository.deleteAll();
		}
	}

	private String orderURL() {
		return "http://localhost:" + serverPort;
	}

	@Test
	public void IsOrderFormDisplayed() {
		ResponseEntity<String> resultEntity = restTemplate.getForEntity(orderURL() + "/form", String.class);
		assertTrue(resultEntity.getStatusCode().is2xxSuccessful());
		assertTrue(resultEntity.getBody().contains("<form"));
	}

	@Test
	public void IsSubmittedOrderSaved() {
		long before = orderRepository.count();
		MultiValueMap<String, String> map = new LinkedMultiValueMap<String, String>();
		map.add("submit", "");
		map.add("customer", Long.toString(customer.getCustomerId()));
		map.add("orderLine[0].item", Long.toString(item.getItemId()));
		map.add("orderLine[0].count", "42");
		URI uri = restTemplate.postForLocation(orderURL(), map, String.class);
		UriTemplate uriTemplate = new UriTemplate(orderURL() + "/{id}");
		assertEquals(before + 1, orderRepository.count());
	}
}
