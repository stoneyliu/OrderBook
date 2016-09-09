package com.example.rmi.orderbook;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.rmi.RemoteException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.example.rmi.orderbook.client.OrderBookClientHandle;

/**
 * Tests for edge cases in the PriorityOrderBook.
 * 
 * Some sources consulted:
 * http://money.stackexchange.com/questions/45983/price-time-priority-order-matching-limit-order-starvation
 * http://money.stackexchange.com/questions/15156/how-do-exchanges-match-limit-orders
 */
public class OrderBookServiceTest {

	private static final String SELLER1 = "seller1";
	private static final String SELLER2 = "seller2";
	private static final String BUYER1 = "buyer1";
	private static final String BUYER2 = "buyer2";

	private static final String SECURITY = "AAPL";
	private OrderBookClientHandle clientHandler;
	private PriorityOrderBook book;

	@Before
	public final void before() {
		clientHandler = mock(OrderBookClientHandle.class);
		book = new PriorityOrderBook();
	}
	/**
	 * Two identical sales are queued while a valid buyer arrives.
	 * 
	 * Expected: The first sale order placed should be favored (via timestamp).
	 * @throws RemoteException
	 */
	@Test
	public void testTwoIdenticalSales() throws RemoteException {
		Order saleOrder1 = new Order(SELLER1, SECURITY, 1, 10.0,
				false , System.currentTimeMillis(), clientHandler);
		Order saleOrder2 = new Order(SELLER2, SECURITY, 1, 10.0,
				false , System.currentTimeMillis(), clientHandler);

		book.sell(saleOrder1);
		book.sell(saleOrder2);

		Order buyOrder = new Order(BUYER1, SECURITY, 1, 10.0,
				true , System.currentTimeMillis(), clientHandler);
		book.buy(buyOrder);

		List<Order> remainingOrders = book.getAllOrders();
		//Only one order should be in the book
		assertEquals(1, remainingOrders.size());
		//and it should be order2 (because we favored the first one).
		assertEquals(saleOrder2 , remainingOrders.iterator().next());

	}

	/**
	 * Two identical purchase orders are queued while a valid seller arrives.
	 * 
	 * Expected: The first buy order placed should be favored (via time stamp).
	 * @throws RemoteException
	 */
	@Test
	public void testTwoIdenticalPurchases() throws RemoteException {
		Order buyOrder1 = new Order(BUYER1, SECURITY, 1, 10.0,
				true , System.currentTimeMillis(), clientHandler);
		Order buyOrder2 = new Order(BUYER2, SECURITY, 1, 10.0,
				true , System.currentTimeMillis(), clientHandler);

		book.buy(buyOrder1);
		book.buy(buyOrder2);

		Order sellOrder = new Order(SELLER1, SECURITY, 1, 10.0,
				false , System.currentTimeMillis(), clientHandler);
		book.sell(sellOrder);
		List<Order> remainingOrders = book.getAllOrders();
		//Only one order should be in the book
		assertEquals(remainingOrders.size(),1);		
		//and it should be order2 (because we favored the first one).
		assertEquals(remainingOrders.iterator().next(), buyOrder2);
	}

	/**
	 * A buyer places an order for more than the highest offer queued.
	 * 
	 * Expected: The transaction should occur at the highest offer 
	 * (i.e less than what the buyer actually placed.)
	 * @throws RemoteException
	 */
	@Test
	public void testBuyerPaysLessThanExpected() throws RemoteException {
		//BUYS and pays less than expected (20.10)
		//		ISBUYING=NO SECURITY=AAPL AMOUNT=1 VALUE=20.10  AT="2016-08-24 11:36:00"
		//		ISBUYING=YES SECURITY=AAPL AMOUNT=1 VALUE=40  AT="2016-08-24 11:36:01"
		Order sellOrder = new Order(SELLER1, SECURITY, 1, 20.10,
				false , System.currentTimeMillis(), clientHandler);

		assertEquals(new Double(0.0), book.sell(sellOrder));

		Order buyOrder = new Order(BUYER1, SECURITY, 1, 40.0,
				true , System.currentTimeMillis(), clientHandler);

		Double transactionValue = book.buy(buyOrder);
		assertEquals(new Double(20.10), transactionValue);		
	}

	/**
	 * A seller places an order for less than the lowest bid queued.
	 * 
	 * Expected: The transaction should occur for the HIGHEST bidder
	 * at the sellers price (LOWEST)
	 * @throws RemoteException
	 */
	@Test
	public void testSellerGetsMoreThanExpected() throws RemoteException {
		//Sells and gets more than expected (20.21)
		//		ISBUYING=YES SECURITY=AAPL AMOUNT=1 VALUE=20.21  AT="2016-08-24 11:36:00"
		//		ISBUYING=NO SECURITY=AAPL AMOUNT=1 VALUE=20.10  AT="2016-08-24 11:36:01"					
		Order buyOrder = new Order(BUYER1, SECURITY, 1, 20.21,
				true , System.currentTimeMillis(), clientHandler);

		assertEquals(new Double(0.0), book.buy(buyOrder));

		Order sellOrder = new Order(SELLER1, SECURITY, 1, 20.10,
				false , System.currentTimeMillis(), clientHandler);

		Double transactionValue = book.sell(sellOrder);		
		assertEquals(new Double(20.21), transactionValue);
	}

	/**
	 * A sell order arrives for less units than a queued applicable buy order.
	 * 
	 * Expected: We partially fulfill the order thus satisfying the buyer 
	 * and partially completing the sellers order.
	 * @throws RemoteException
	 */
	@Test
	public void partialSale() throws RemoteException {
		Order buyOrder = new Order(BUYER1, SECURITY, 2, 10.0,
				true , System.currentTimeMillis(), clientHandler);

		book.buy(buyOrder);

		Order sellOrder = new Order(SELLER1, SECURITY, 1, 10.0,
				false , System.currentTimeMillis(), clientHandler);
		book.sell(sellOrder);

		List<Order> remainingOrders = book.getAllOrders();
		//The buy order remains
		assertEquals(1, remainingOrders.size());

		Order remainingOrder = remainingOrders.iterator().next();
		// The same one we have.		
		assertEquals(buyOrder, remainingOrder);

		//but with less units
		assertEquals(new Integer(1), remainingOrder.getUnits());
	}

	/**
	 * A buy order arrives for less units than a queued applicable sale order.
	 * 
	 * Expected: We partially fulfill the order thus satisfying the buyer 
	 * and partially completing the sellers order.
	 * @throws RemoteException
	 */
	@Test
	public void partialBuy() throws RemoteException {
		Order sellOrder = new Order(SELLER1, SECURITY, 2, 9.0,
				false , System.currentTimeMillis(), clientHandler);

		book.sell(sellOrder);

		Order buyOrder = new Order(BUYER1, SECURITY, 1, 10.0,
				true , System.currentTimeMillis(), clientHandler);
		book.buy(buyOrder);
		List<Order> remainingOrders = book.getAllOrders();
		//The sale order remains
		assertEquals(1, remainingOrders.size());

		Order remainingOrder = remainingOrders.iterator().next();
		// The same one we have.		
		assertEquals(sellOrder, remainingOrder);

		//but with less units
		assertEquals(new Integer(1), remainingOrder.getUnits());
	}

	/**
	 * Users aren't allowed to make transactions with themselves.
	 * They could mess up the market!
	 * @throws RemoteException
	 * 			if the connection drops
	 */
	@Test(expected = IllegalArgumentException.class)
	public void cantBuyToYourself() throws RemoteException {
		Order sellOrder = new Order(SELLER1, SECURITY, 1, 10.0,
				false , System.currentTimeMillis(), clientHandler);
		book.sell(sellOrder);

		Order buyOrder = new Order(SELLER1, SECURITY, 1, 10.0,
				true , System.currentTimeMillis(), clientHandler);
		book.buy(buyOrder);
	}

	/**
	 * Users aren't allowed to make transactions with themselves.
	 * They could mess up the market!
	 * @throws RemoteException
	 * 			if the connection drops
	 */
	@Test(expected = IllegalArgumentException.class)
	public void cantSellToYourself() throws RemoteException {
		Order buyOrder = new Order(SELLER1, SECURITY, 1, 10.0,
				true , System.currentTimeMillis(), clientHandler);
		book.buy(buyOrder);

		Order sellOrder = new Order(SELLER1, SECURITY, 1, 9.0,
				false , System.currentTimeMillis(), clientHandler);
		book.sell(sellOrder);
	}
	
	/**
	 * Testing that the SellSideComparator sorts from less value to great value.
	 * (i.e Natural Ordering)
	 * @throws RemoteException
	 */
	@Test
	public void sellingComparator() throws RemoteException {
		//The "cheapest" sell order in value is the BEST candidate to get
		//a buyer, put it first in the pq.
		PriorityOrderBook.SellSideComparator comp = new PriorityOrderBook.SellSideComparator();

		Order one = new Order(SELLER1, SECURITY, 1, 9.0,
				false , 1, clientHandler);

		Order two = new Order(SELLER1, SECURITY, 1, 10.0,
				false , 1, clientHandler);	

		int lessThan = comp.compare(one, two);

		//same time, lowest value goes first in the pq
		assertEquals(true, new Boolean(lessThan < 0 ) );

		one = new Order(SELLER1, SECURITY, 1, 10.0,
				false , 1, clientHandler);

		two = new Order(SELLER2, SECURITY, 1, 10.0,
				false , 2, clientHandler);

		int equalButTimeWins = comp.compare(one, two);
		//same value, earliest to arrive goes first in the pq
		assertEquals(true, new Boolean(equalButTimeWins < 0 ) );


		one = new Order(SELLER1, SECURITY, 1, 10.0,
				false , 1, clientHandler);

		two = new Order(SELLER1, SECURITY, 1, 9.0,
				false , 1, clientHandler);	

		int greaterThan = comp.compare(one, two);
		//same time, lowest value wins
		assertEquals(true, new Boolean(greaterThan > 0 ) );


	}

	/**
	 * Testing that the BuySideComparator sorts from greater value to less value.
	 * @throws RemoteException
	 */
	@Test
	public void buyingComparator() throws RemoteException {
		PriorityOrderBook.BuySideComparator comp = new PriorityOrderBook.BuySideComparator();
		Order one = new Order(BUYER1, SECURITY, 1, 10.0,
				true , 1, clientHandler);

		Order two = new Order(BUYER2, SECURITY, 1, 10.0,
				true , 2, clientHandler);

		int equalButTimeWins = comp.compare(one, two);

		assertEquals(true, new Boolean(equalButTimeWins < 0 ) );


		one = new Order(BUYER1, SECURITY, 1, 10.0,
				true , 1, clientHandler);

		two = new Order(BUYER2, SECURITY, 1, 9.0,
				true , 1, clientHandler);	

		int greaterThan = comp.compare(one, two);
		//same time, highest value wins
		assertEquals(true, new Boolean(greaterThan < 0 ) );


		one = new Order(BUYER1, SECURITY, 1, 9.0,
				true , 1, clientHandler);

		two = new Order(BUYER2, SECURITY, 1, 10.0,
				true , 1, clientHandler);	

		int lessThan = comp.compare(one, two);
		//same time, highest value wins
		assertEquals(true, new Boolean(lessThan > 0 ) );

	}

	/**
	 * A sell order arrives for less units than several queued applicable buy orders.
	 * 
	 * Expected: We partially fulfill the order thus satisfying the buyer 
	 * and partially completing the sellers order.
	 * @throws RemoteException
	 */
	@Test
	public void advancedPartialSell() throws RemoteException {
		//		SECURITY=GOOG AMOUNT=500 VALUE=430.0 ISBUYING=YES
		//
		//		SECURITY=GOOG AMOUNT=1000 VALUE=435.5 ISBUYING=YES
		//
		//		SECURITY=GOOG AMOUNT=1200 VALUE=429.0 ISBUYING=NO
		
		Order one = new Order(BUYER1, SECURITY, 500, 430.0,
				true , 1, clientHandler);
		
		book.buy(one);
		
		Order two = new Order(BUYER2, SECURITY, 1000, 435.5,
				true , 2, clientHandler);
		book.buy(two);
		
		Order three = new Order(SELLER1, SECURITY, 1200, 429.0,
				false , 3, clientHandler);
		
		Double transactionValue = book.sell(three);
		
		List<Order> remainingOrders = book.getAllOrders();
		//Only one order should be in the book
		assertEquals(1, remainingOrders.size());
		//it should be order1 because we satisfied order2 first
		Order remainingOrder = remainingOrders.iterator().next();
		assertEquals(one , remainingOrder);
		//and it still has 300 units to place.		
		assertEquals(new Integer(300) , remainingOrder.getUnits());
		//the transaction ran at this value, considering the units traded.
		assertEquals(new Double(435.5*1000 + 430.0*200) ,transactionValue);
	}
	
	/**
	 * Order Update
	 * 1. If price changes, you lose priority regardless of the rest of the values.
	 * @throws RemoteException
	 */
	@Test
	public void updateOrderPriceChanges() throws RemoteException {
		
		int orderUnits = 20;
		
		Order one = new Order(BUYER1, SECURITY, orderUnits, 20.0,
				true , System.currentTimeMillis(), clientHandler);
		
		book.buy(one);
		
		Order two = new Order(BUYER2, SECURITY, orderUnits, 20.0,
				true , System.currentTimeMillis(), clientHandler);
		
		book.buy(two);
		
		System.out.println(book);
		
		Order oneUpdate = new Order(one.getOrderId(), BUYER1, SECURITY, orderUnits/2, 19.0,
				true , System.currentTimeMillis() + 1000, clientHandler);
		book.update(oneUpdate);
		
		
		List<Order> remainingOrders = book.getAllOrders();
		System.out.println(book);
		
		//order one had the lead but lost it because there was a price change.
		Order leadingOrder = remainingOrders.iterator().next();
		assertEquals(two, leadingOrder);		
		
	}
	
	/**
	 * Update order
	 * 2. If quantity increases, even if price remains the same, you lose priority.
	 * @throws RemoteException
	 */
	@Test
	public void updateOrderQuantityIncreases() throws RemoteException {
		
		int orderUnits = 10;
		Long t1 = 100000L;
		
		Order one = new Order(BUYER1, SECURITY, orderUnits, 20.0,
				true , t1, clientHandler);
		
		book.buy(one);
		
		Order two = new Order(BUYER2, SECURITY, orderUnits, 20.0,
				true , t1 + 100000, clientHandler);
		
		book.buy(two);
		
		System.out.println(book);
		
		Order oneUpdate = new Order(one.getOrderId(), BUYER1, SECURITY, orderUnits*2, 20.0,
				true , t1 + 100000*2, clientHandler);
		book.update(oneUpdate);
		
		
		List<Order> remainingOrders = book.getAllOrders();
		System.out.println(book);
		
		//order one had the lead but goes down the queue after the update.
		Order leadingOrder = remainingOrders.iterator().next();
		assertEquals(two, leadingOrder);		
	}
	
	/**
	 * Update Order
	 * 3. **ONLY** If quantity decreases and price remains the same, you keep priority.
	 * @throws RemoteException
	 */
	@Test
	public void updateOrderQuantityDecreases() throws RemoteException {
		
		int orderUnits = 20;
		
		Order one = new Order(BUYER1, SECURITY, orderUnits, 20.0,
				true , System.currentTimeMillis(), clientHandler);
		
		book.buy(one);
		
		Order two = new Order(BUYER2, SECURITY, orderUnits, 20.0,
				true , System.currentTimeMillis(), clientHandler);
		book.buy(two);
		System.out.println(book);
		Order oneUpdate = new Order(one.getOrderId(), BUYER1, SECURITY, orderUnits/2, 20.0,
				true , System.currentTimeMillis() + 1000, clientHandler);
		book.update(oneUpdate);
		
		
		List<Order> remainingOrders = book.getAllOrders();
		System.out.println(book);
		
		//order one had the lead and remains leading.
		Order leadingOrder = remainingOrders.iterator().next();
		assertEquals(one, leadingOrder);		
		
	}	

}
