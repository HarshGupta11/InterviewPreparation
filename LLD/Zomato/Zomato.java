import java.util.*;
import java.util.stream.Collectors;

// ========================== ENUMS ==========================

enum OrderStatus {
    PLACED,
    CONFIRMED,
    PREPARING,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED
}

enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    REFUNDED
}

enum OrderType {
    DELIVERY,
    PICKUP
}

// ========================== OBSERVER PATTERN ==========================

interface Observer {
    void update(String event, Object data);
}

abstract class Observable {
    private final List<Observer> observers = new ArrayList<>();

    public void addObserver(Observer observer) {
        observers.add(observer);
    }

    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    public void notifyObservers(String event, Object data) {
        for (Observer observer : observers) {
            observer.update(event, data);
        }
    }
}

// ========================== MENU ITEM (Model) ==========================

class MenuItem {
    private final String id;
    private String itemName;
    private double itemPrice;
    private boolean available;

    public MenuItem(String itemName, double itemPrice) {
        this.id = UUID.randomUUID().toString();
        this.itemName = itemName;
        this.itemPrice = itemPrice;
        this.available = true;
    }

    public String getId() {
        return id;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public double getItemPrice() {
        return itemPrice;
    }

    public void setItemPrice(double itemPrice) {
        this.itemPrice = itemPrice;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MenuItem menuItem = (MenuItem) o;
        return Objects.equals(id, menuItem.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return itemName + " - ₹" + itemPrice + (available ? "" : " [OUT OF STOCK]");
    }
}

// ========================== MENU ==========================

class Menu {
    private final List<MenuItem> items;

    public Menu() {
        this.items = new ArrayList<>();
    }

    public void display() {
        System.out.println("---- Menu ----");
        for (int i = 0; i < items.size(); i++) {
            System.out.println((i + 1) + ". " + items.get(i));
        }
        System.out.println("--------------");
    }

    public void addItem(MenuItem item) {
        items.add(item);
    }

    public void removeItem(MenuItem item) {
        items.remove(item);
    }

    public List<MenuItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public boolean isItemAvailable(MenuItem item) {
        return items.contains(item) && item.isAvailable();
    }
}

// ========================== RESTAURANT ==========================

class Restaurant implements Observer {
    private final String id;
    private String name;
    private String address;
    private String timing;
    private final Menu menu;

    public Restaurant(String name, String address, String timing) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.address = address;
        this.timing = timing;
        this.menu = new Menu();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getTiming() {
        return timing;
    }

    public void setTiming(String timing) {
        this.timing = timing;
    }

    public Menu getMenu() {
        return menu;
    }

    public void markItemOutOfStock(MenuItem item) {
        item.setAvailable(false);
        System.out.println("[Restaurant: " + name + "] Marked '" + item.getItemName() + "' as out of stock.");
    }

    public void markItemInStock(MenuItem item) {
        item.setAvailable(true);
        System.out.println("[Restaurant: " + name + "] Marked '" + item.getItemName() + "' as back in stock.");
    }

    @Override
    public void update(String event, Object data) {
        if ("ORDER_PLACED".equals(event) && data instanceof Order) {
            Order order = (Order) data;
            System.out.println("[Restaurant: " + name + "] New order received! Order ID: " + order.getOrderId());
            System.out.println("  Items:");
            for (Map.Entry<MenuItem, Integer> entry : order.getItems().entrySet()) {
                System.out.println("    - " + entry.getKey().getItemName() + " x" + entry.getValue());
            }
        } else if ("ORDER_CANCELLED".equals(event) && data instanceof Order) {
            Order order = (Order) data;
            System.out.println("[Restaurant: " + name + "] Order cancelled. Order ID: " + order.getOrderId());
        }
    }

    @Override
    public String toString() {
        return name + " (" + address + ") | Timing: " + timing;
    }
}

// ========================== RESTAURANT MANAGER (Singleton) ==========================

class RestaurantManager {
    private static volatile RestaurantManager instance;
    private final Map<String, Restaurant> restaurants; // id -> Restaurant

    private RestaurantManager() {
        this.restaurants = new HashMap<>();
    }

    public static RestaurantManager getInstance() {
        if (instance == null) {
            synchronized (RestaurantManager.class) {
                if (instance == null) {
                    instance = new RestaurantManager();
                }
            }
        }
        return instance;
    }

    public void addRestaurant(Restaurant restaurant) {
        restaurants.put(restaurant.getId(), restaurant);
        System.out.println("[RestaurantManager] Added restaurant: " + restaurant.getName());
    }

    public void removeRestaurant(String restaurantId) {
        Restaurant removed = restaurants.remove(restaurantId);
        if (removed != null) {
            System.out.println("[RestaurantManager] Removed restaurant: " + removed.getName());
        }
    }

    public Restaurant getRestaurant(String restaurantId) {
        return restaurants.get(restaurantId);
    }

    public List<Restaurant> searchRestaurants(String query) {
        String lowerQuery = query.toLowerCase();
        return restaurants.values().stream()
                .filter(r -> r.getName().toLowerCase().contains(lowerQuery)
                        || r.getAddress().toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
    }

    public List<Restaurant> getAllRestaurants() {
        return new ArrayList<>(restaurants.values());
    }
}

// ========================== CART ==========================

class Cart {
    private final Map<MenuItem, Integer> items;
    private String restaurantId;

    public Cart() {
        this.items = new HashMap<>();
        this.restaurantId = null;
    }

    public void addItem(MenuItem item, String restaurantId) {
        // Cart can only contain items from one restaurant at a time
        if (this.restaurantId != null && !this.restaurantId.equals(restaurantId)) {
            System.out.println("[Cart] Cannot add items from different restaurants. Clear cart first.");
            return;
        }
        this.restaurantId = restaurantId;
        items.put(item, items.getOrDefault(item, 0) + 1);
        System.out.println("[Cart] Added: " + item.getItemName() + " (qty: " + items.get(item) + ")");
    }

    public void updateQuantity(MenuItem item, int quantity) {
        if (quantity <= 0) {
            items.remove(item);
            System.out.println("[Cart] Removed: " + item.getItemName());
            if (items.isEmpty()) {
                restaurantId = null;
            }
        } else {
            items.put(item, quantity);
            System.out.println("[Cart] Updated: " + item.getItemName() + " -> qty: " + quantity);
        }
    }

    public void clear() {
        items.clear();
        restaurantId = null;
        System.out.println("[Cart] Cart cleared.");
    }

    public Map<MenuItem, Integer> getItems() {
        return Collections.unmodifiableMap(items);
    }

    public String getRestaurantId() {
        return restaurantId;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public double getTotal() {
        double total = 0;
        for (Map.Entry<MenuItem, Integer> entry : items.entrySet()) {
            total += entry.getKey().getItemPrice() * entry.getValue();
        }
        return total;
    }

    public void displayCart() {
        if (items.isEmpty()) {
            System.out.println("[Cart] Cart is empty.");
            return;
        }
        System.out.println("---- Cart ----");
        for (Map.Entry<MenuItem, Integer> entry : items.entrySet()) {
            System.out.println("  " + entry.getKey().getItemName() + " x" + entry.getValue()
                    + " = ₹" + (entry.getKey().getItemPrice() * entry.getValue()));
        }
        System.out.println("  Total: ₹" + getTotal());
        System.out.println("--------------");
    }
}

// ========================== USER ==========================

class User implements Observer {
    private final String id;
    private String name;
    private String phoneNumber;
    private String email;
    private String address;
    private final Cart cart;

    public User(String name, String phoneNumber, String email, String address) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.address = address;
        this.cart = new Cart();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Cart getCart() {
        return cart;
    }

    public void addItemToCart(MenuItem item, String restaurantId) {
        if (!item.isAvailable()) {
            System.out.println("[User: " + name + "] Item '" + item.getItemName() + "' is out of stock.");
            return;
        }
        cart.addItem(item, restaurantId);
    }

    @Override
    public void update(String event, Object data) {
        if ("ORDER_CONFIRMED".equals(event) && data instanceof Order) {
            Order order = (Order) data;
            System.out.println("[User: " + name + "] Your order " + order.getOrderId() + " has been confirmed!");
        } else if ("ORDER_STATUS_CHANGED".equals(event) && data instanceof Order) {
            Order order = (Order) data;
            System.out.println("[User: " + name + "] Order " + order.getOrderId()
                    + " status updated to: " + order.getOrderStatus());
        }
    }

    @Override
    public String toString() {
        return name + " (" + email + ")";
    }
}

// ========================== ORDER (Observable) ==========================

class Order extends Observable {
    private final String orderId;
    private final Map<MenuItem, Integer> items;
    private final String customerId;
    private final String restaurantId;
    private OrderStatus orderStatus;
    private PaymentStatus paymentStatus;
    private final OrderType orderType;

    public Order(Map<MenuItem, Integer> items, String customerId, String restaurantId, OrderType orderType) {
        this.orderId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.items = new HashMap<>(items);
        this.customerId = customerId;
        this.restaurantId = restaurantId;
        this.orderStatus = OrderStatus.PLACED;
        this.paymentStatus = PaymentStatus.PENDING;
        this.orderType = orderType;
    }

    public String getOrderId() {
        return orderId;
    }

    public Map<MenuItem, Integer> getItems() {
        return Collections.unmodifiableMap(items);
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getRestaurantId() {
        return restaurantId;
    }

    public OrderStatus getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
        notifyObservers("ORDER_STATUS_CHANGED", this);
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public double getTotalAmount() {
        double total = 0;
        for (Map.Entry<MenuItem, Integer> entry : items.entrySet()) {
            total += entry.getKey().getItemPrice() * entry.getValue();
        }
        return total;
    }

    @Override
    public String toString() {
        return "Order[" + orderId + "] Status: " + orderStatus + " | Payment: " + paymentStatus
                + " | Type: " + orderType + " | Total: ₹" + getTotalAmount();
    }
}

// ========================== DELIVERY ORDER ==========================

class DeliveryOrder extends Order {
    private String deliveryAddress;

    public DeliveryOrder(Map<MenuItem, Integer> items, String customerId, String restaurantId, String deliveryAddress) {
        super(items, customerId, restaurantId, OrderType.DELIVERY);
        this.deliveryAddress = deliveryAddress;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }
}

// ========================== PICKUP ORDER ==========================

class PickupOrder extends Order {
    private String estimatedPickupTime;

    public PickupOrder(Map<MenuItem, Integer> items, String customerId, String restaurantId) {
        super(items, customerId, restaurantId, OrderType.PICKUP);
        this.estimatedPickupTime = "20 mins"; // default estimate
    }

    public String getEstimatedPickupTime() {
        return estimatedPickupTime;
    }

    public void setEstimatedPickupTime(String estimatedPickupTime) {
        this.estimatedPickupTime = estimatedPickupTime;
    }
}

// ========================== ORDER MANAGER (Singleton) ==========================

class OrderManager {
    private static volatile OrderManager instance;
    private final Map<String, Order> orders; // orderId -> Order

    private OrderManager() {
        this.orders = new HashMap<>();
    }

    public static OrderManager getInstance() {
        if (instance == null) {
            synchronized (OrderManager.class) {
                if (instance == null) {
                    instance = new OrderManager();
                }
            }
        }
        return instance;
    }

    /**
     * Places an order after validating cart items against the restaurant's menu.
     * Registers the restaurant and user as observers on the order.
     */
    public Order placeOrder(User user, OrderType orderType) {
        Cart cart = user.getCart();

        if (cart.isEmpty()) {
            System.out.println("[OrderManager] Cannot place order: Cart is empty.");
            return null;
        }

        String restaurantId = cart.getRestaurantId();
        Restaurant restaurant = RestaurantManager.getInstance().getRestaurant(restaurantId);

        if (restaurant == null) {
            System.out.println("[OrderManager] Cannot place order: Restaurant not found.");
            return null;
        }

        // Validate items are still available
        List<MenuItem> unavailableItems = new ArrayList<>();
        for (MenuItem item : cart.getItems().keySet()) {
            if (!restaurant.getMenu().isItemAvailable(item)) {
                unavailableItems.add(item);
            }
        }

        if (!unavailableItems.isEmpty()) {
            System.out.println("[OrderManager] The following items are no longer available:");
            for (MenuItem item : unavailableItems) {
                System.out.println("  - " + item.getItemName());
            }
            System.out.println("  Please update your cart and try again.");
            return null;
        }

        // Create order based on type
        Order order;
        if (orderType == OrderType.DELIVERY) {
            order = new DeliveryOrder(cart.getItems(), user.getId(), restaurantId, user.getAddress());
        } else {
            order = new PickupOrder(cart.getItems(), user.getId(), restaurantId);
        }

        // Register observers: restaurant gets notified of new orders, user gets status updates
        order.addObserver(restaurant);
        order.addObserver(user);

        // Simulate payment success
        order.setPaymentStatus(PaymentStatus.PAID);

        // Store order
        orders.put(order.getOrderId(), order);

        // Notify observers about the placed order
        order.notifyObservers("ORDER_PLACED", order);

        System.out.println("[OrderManager] Order placed successfully! " + order);

        // Clear the cart after successful order
        cart.clear();

        return order;
    }

    public void cancelOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) {
            System.out.println("[OrderManager] Order not found: " + orderId);
            return;
        }
        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        order.notifyObservers("ORDER_CANCELLED", order);
        System.out.println("[OrderManager] Order " + orderId + " has been cancelled and refunded.");
    }

    public void removeOrder(String orderId) {
        Order removed = orders.remove(orderId);
        if (removed != null) {
            System.out.println("[OrderManager] Removed order: " + orderId);
        }
    }

    public Order getOrder(String orderId) {
        return orders.get(orderId);
    }

    public List<Order> getAllOrders() {
        return new ArrayList<>(orders.values());
    }
}

// ========================== ZOMATO (Orchestrator / Main) ==========================

public class Zomato {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("          ZOMATO - Food Delivery System          ");
        System.out.println("=================================================\n");

        // --- Setup Restaurants ---
        RestaurantManager restaurantManager = RestaurantManager.getInstance();

        Restaurant pizzaPlace = new Restaurant("Pizza Palace", "123 MG Road, Bangalore", "10:00 AM - 11:00 PM");
        MenuItem margherita = new MenuItem("Margherita Pizza", 299.0);
        MenuItem farmhouse = new MenuItem("Farmhouse Pizza", 399.0);
        MenuItem garlichBread = new MenuItem("Garlic Bread", 149.0);
        MenuItem coldDrink = new MenuItem("Cold Drink", 60.0);
        pizzaPlace.getMenu().addItem(margherita);
        pizzaPlace.getMenu().addItem(farmhouse);
        pizzaPlace.getMenu().addItem(garlichBread);
        pizzaPlace.getMenu().addItem(coldDrink);
        restaurantManager.addRestaurant(pizzaPlace);

        Restaurant biryaniHouse = new Restaurant("Biryani House", "456 Koramangala, Bangalore", "11:00 AM - 10:00 PM");
        MenuItem chickenBiryani = new MenuItem("Chicken Biryani", 249.0);
        MenuItem vegBiryani = new MenuItem("Veg Biryani", 199.0);
        MenuItem raita = new MenuItem("Raita", 49.0);
        biryaniHouse.getMenu().addItem(chickenBiryani);
        biryaniHouse.getMenu().addItem(vegBiryani);
        biryaniHouse.getMenu().addItem(raita);
        restaurantManager.addRestaurant(biryaniHouse);

        // --- Display Restaurants ---
        System.out.println("\n--- All Restaurants ---");
        for (Restaurant r : restaurantManager.getAllRestaurants()) {
            System.out.println("  " + r);
        }

        // --- Search Restaurants ---
        System.out.println("\n--- Search: 'pizza' ---");
        List<Restaurant> searchResults = restaurantManager.searchRestaurants("pizza");
        for (Restaurant r : searchResults) {
            System.out.println("  Found: " + r);
        }

        // --- Display Menu ---
        System.out.println("\n--- Pizza Palace Menu ---");
        pizzaPlace.getMenu().display();

        // --- Create User ---
        User user = new User("Priya", "9876543210", "priya@email.com", "789 Indiranagar, Bangalore");
        System.out.println("\n--- User: " + user + " ---");

        // --- Add Items to Cart ---
        System.out.println("\n--- Adding items to cart ---");
        user.addItemToCart(margherita, pizzaPlace.getId());
        user.addItemToCart(garlichBread, pizzaPlace.getId());
        user.addItemToCart(coldDrink, pizzaPlace.getId());
        user.getCart().updateQuantity(margherita, 2); // want 2 pizzas

        // --- Display Cart ---
        System.out.println("\n--- Cart Contents ---");
        user.getCart().displayCart();

        // --- Place Order (Delivery) ---
        System.out.println("\n--- Placing Delivery Order ---");
        OrderManager orderManager = OrderManager.getInstance();
        Order order = orderManager.placeOrder(user, OrderType.DELIVERY);

        // --- Simulate Order Status Update ---
        if (order != null) {
            System.out.println("\n--- Updating Order Status ---");
            order.setOrderStatus(OrderStatus.CONFIRMED);
            order.setOrderStatus(OrderStatus.PREPARING);
            order.setOrderStatus(OrderStatus.OUT_FOR_DELIVERY);
            order.setOrderStatus(OrderStatus.DELIVERED);
        }

        // --- Demonstrate Out of Stock scenario ---
        System.out.println("\n\n--- Out of Stock Scenario ---");
        pizzaPlace.markItemOutOfStock(farmhouse);

        User user2 = new User("Rahul", "9123456789", "rahul@email.com", "101 HSR Layout, Bangalore");
        System.out.println("\n--- User2 trying to add out-of-stock item ---");
        user2.addItemToCart(farmhouse, pizzaPlace.getId()); // should fail - out of stock
        user2.addItemToCart(margherita, pizzaPlace.getId()); // should succeed

        System.out.println("\n--- User2 Cart ---");
        user2.getCart().displayCart();

        // --- Place Pickup Order ---
        System.out.println("\n--- Placing Pickup Order ---");
        Order pickupOrder = orderManager.placeOrder(user2, OrderType.PICKUP);

        // --- Show all orders ---
        System.out.println("\n--- All Orders ---");
        for (Order o : orderManager.getAllOrders()) {
            System.out.println("  " + o);
        }

        System.out.println("\n=================================================");
        System.out.println("                  Demo Complete                   ");
        System.out.println("=================================================");
    }
}
