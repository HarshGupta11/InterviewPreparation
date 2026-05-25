import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

// ========================== ENUMS ==========================

enum CouponType {
    FLAT,
    PERCENTAGE,
    PERCENTAGE_WITH_CAP
}

enum CouponScope {
    CART_LEVEL,
    PRODUCT_LEVEL
}

// ========================== MODELS ==========================

class Product {
    private final String id;
    private final String name;
    private final double price;

    public Product(String name, double price) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = name;
        this.price = price;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return Objects.equals(id, product.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name + " (₹" + price + ")";
    }
}

class User {
    private final String id;
    private final String name;
    private final String address;
    private final boolean isLoyalty;
    private final Cart cart;

    public User(String name, String address, boolean isLoyalty) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = name;
        this.address = address;
        this.isLoyalty = isLoyalty;
        this.cart = new Cart();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public boolean isLoyalty() {
        return isLoyalty;
    }

    public Cart getCart() {
        return cart;
    }

    @Override
    public String toString() {
        return name + (isLoyalty ? " [LOYALTY]" : "");
    }
}

class Cart {
    private final List<Product> products;
    private final List<Coupon> appliedCoupons;

    public Cart() {
        this.products = new ArrayList<>();
        this.appliedCoupons = new ArrayList<>();
    }

    public void addProduct(Product product) {
        products.add(product);
    }

    public void removeProduct(Product product) {
        products.remove(product);
    }

    public List<Product> getProducts() {
        return Collections.unmodifiableList(products);
    }

    public List<Coupon> getAppliedCoupons() {
        return Collections.unmodifiableList(appliedCoupons);
    }

    public void addAppliedCoupon(Coupon coupon) {
        appliedCoupons.add(coupon);
    }

    public void removeAppliedCoupon(Coupon coupon) {
        appliedCoupons.remove(coupon);
    }

    public void clearCoupons() {
        appliedCoupons.clear();
    }

    /**
     * Returns the raw subtotal (sum of all product prices, no discounts applied).
     */
    public double getTotalAmount() {
        return products.stream()
                .mapToDouble(Product::getPrice)
                .sum();
    }

    /**
     * Returns subtotal for only the products matching the given IDs.
     */
    public double getProductSubtotal(Set<String> productIds) {
        return products.stream()
                .filter(p -> productIds.contains(p.getId()))
                .mapToDouble(Product::getPrice)
                .sum();
    }

    /**
     * Returns the final price after applying all coupons (additive model).
     * Each coupon calculates its discount independently on the original amounts.
     */
    public double getFinalPrice() {
        double totalDiscount = appliedCoupons.stream()
                .mapToDouble(coupon -> coupon.applyDiscount(this))
                .sum();
        return Math.max(0, getTotalAmount() - totalDiscount);
    }

    public boolean isEmpty() {
        return products.isEmpty();
    }

    public void displayCart() {
        if (products.isEmpty()) {
            System.out.println("  [Cart is empty]");
            return;
        }
        System.out.println("  ---- Cart ----");
        for (Product product : products) {
            System.out.println("    " + product);
        }
        System.out.println("  Subtotal: ₹" + String.format("%.2f", getTotalAmount()));
        if (!appliedCoupons.isEmpty()) {
            System.out.println("  Applied Coupons: " + appliedCoupons.stream()
                    .map(Coupon::getCouponCode)
                    .collect(Collectors.joining(", ")));
            System.out.println("  Final Price: ₹" + String.format("%.2f", getFinalPrice()));
        }
        System.out.println("  ---------------");
    }
}

// ========================== STRATEGY PATTERN ==========================

interface DiscountStrategy {
    double calculateDiscount(double amount);
    String getDescription();
}

class FlatDiscountStrategy implements DiscountStrategy {
    private final double flatAmount;

    public FlatDiscountStrategy(double flatAmount) {
        this.flatAmount = flatAmount;
    }

    @Override
    public double calculateDiscount(double amount) {
        return Math.min(flatAmount, amount);
    }

    @Override
    public String getDescription() {
        return "Flat ₹" + flatAmount + " off";
    }
}

class PercentageDiscountStrategy implements DiscountStrategy {
    private final double percentage;

    public PercentageDiscountStrategy(double percentage) {
        this.percentage = percentage;
    }

    @Override
    public double calculateDiscount(double amount) {
        return amount * percentage / 100.0;
    }

    @Override
    public String getDescription() {
        return percentage + "% off";
    }
}

class PercentageWithCapDiscountStrategy implements DiscountStrategy {
    private final double percentage;
    private final double maxCap;

    public PercentageWithCapDiscountStrategy(double percentage, double maxCap) {
        this.percentage = percentage;
        this.maxCap = maxCap;
    }

    @Override
    public double calculateDiscount(double amount) {
        double discount = amount * percentage / 100.0;
        return Math.min(discount, maxCap);
    }

    @Override
    public String getDescription() {
        return percentage + "% off (max ₹" + maxCap + ")";
    }
}

// ========================== COUPON ==========================

class Coupon {
    private final String couponCode;
    private final String description;
    private final DiscountStrategy discountStrategy;
    private final CouponScope scope;
    private final Set<String> applicableProductIds;
    private final double minimumOrderValue;
    private final LocalDateTime expiryDate;
    private final AtomicInteger numberOfAllowedUsages;
    private final boolean stackable;
    private final boolean loyaltyOnly;

    private Coupon(Builder builder) {
        this.couponCode = builder.couponCode;
        this.description = builder.description;
        this.discountStrategy = builder.discountStrategy;
        this.scope = builder.scope;
        this.applicableProductIds = builder.applicableProductIds;
        this.minimumOrderValue = builder.minimumOrderValue;
        this.expiryDate = builder.expiryDate;
        this.numberOfAllowedUsages = new AtomicInteger(builder.numberOfAllowedUsages);
        this.stackable = builder.stackable;
        this.loyaltyOnly = builder.loyaltyOnly;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public String getDescription() {
        return description;
    }

    public DiscountStrategy getDiscountStrategy() {
        return discountStrategy;
    }

    public CouponScope getScope() {
        return scope;
    }

    public Set<String> getApplicableProductIds() {
        return Collections.unmodifiableSet(applicableProductIds);
    }

    public double getMinimumOrderValue() {
        return minimumOrderValue;
    }

    public boolean isStackable() {
        return stackable;
    }

    public boolean isLoyaltyOnly() {
        return loyaltyOnly;
    }

    public int getRemainingUsages() {
        return numberOfAllowedUsages.get();
    }

    /**
     * Validates whether this coupon can be applied to the given cart for the given user.
     * Checks: expiry, remaining usages, loyalty, minimum order value, product applicability, stackability.
     */
    public boolean isApplicable(Cart cart, User user) {
        if (isExpired()) {
            System.out.println("    [Coupon " + couponCode + "] Expired.");
            return false;
        }

        if (!hasRemainingUsages()) {
            System.out.println("    [Coupon " + couponCode + "] No usages remaining.");
            return false;
        }

        if (loyaltyOnly && !user.isLoyalty()) {
            System.out.println("    [Coupon " + couponCode + "] Only for loyalty users.");
            return false;
        }

        if (cart.getTotalAmount() < minimumOrderValue) {
            System.out.println("    [Coupon " + couponCode + "] Min order ₹" + minimumOrderValue
                    + " not met (cart: ₹" + cart.getTotalAmount() + ").");
            return false;
        }

        if (scope == CouponScope.PRODUCT_LEVEL) {
            boolean hasApplicableProduct = cart.getProducts().stream()
                    .anyMatch(p -> applicableProductIds.contains(p.getId()));
            if (!hasApplicableProduct) {
                System.out.println("    [Coupon " + couponCode + "] No applicable products in cart.");
                return false;
            }
        }

        if (!checkStackability(cart)) {
            return false;
        }

        return true;
    }

    /**
     * Calculates the discount amount this coupon provides on the cart.
     * For CART_LEVEL: discount is on the full cart total.
     * For PRODUCT_LEVEL: discount is only on the applicable products' subtotal.
     */
    public double applyDiscount(Cart cart) {
        double applicableAmount;
        if (scope == CouponScope.CART_LEVEL) {
            applicableAmount = cart.getTotalAmount();
        } else {
            applicableAmount = cart.getProductSubtotal(applicableProductIds);
        }
        return discountStrategy.calculateDiscount(applicableAmount);
    }

    /**
     * Thread-safe usage consumption. Returns true if a usage slot was successfully claimed.
     */
    public boolean consumeUsage() {
        while (true) {
            int current = numberOfAllowedUsages.get();
            if (current <= 0) {
                return false;
            }
            if (numberOfAllowedUsages.compareAndSet(current, current - 1)) {
                return true;
            }
        }
    }

    private boolean isExpired() {
        return expiryDate != null && LocalDateTime.now().isAfter(expiryDate);
    }

    private boolean hasRemainingUsages() {
        return numberOfAllowedUsages.get() > 0;
    }

    /**
     * Stackability rules:
     * - If cart already has coupons and this coupon is non-stackable → reject.
     * - If cart has a non-stackable coupon and we try to add any new coupon → reject.
     */
    private boolean checkStackability(Cart cart) {
        List<Coupon> existingCoupons = cart.getAppliedCoupons();

        if (existingCoupons.isEmpty()) {
            return true;
        }

        if (!this.stackable) {
            System.out.println("    [Coupon " + couponCode + "] Non-stackable coupon cannot be added "
                    + "when other coupons are already applied.");
            return false;
        }

        boolean hasNonStackable = existingCoupons.stream().anyMatch(c -> !c.isStackable());
        if (hasNonStackable) {
            System.out.println("    [Coupon " + couponCode + "] Cannot stack on top of a non-stackable coupon.");
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return couponCode + " - " + description + " [" + scope + ", "
                + (stackable ? "stackable" : "non-stackable") + ", usages: "
                + numberOfAllowedUsages.get() + "]";
    }

    // ========================== BUILDER ==========================

    static class Builder {
        private final String couponCode;
        private String description = "";
        private DiscountStrategy discountStrategy;
        private CouponScope scope = CouponScope.CART_LEVEL;
        private Set<String> applicableProductIds = new HashSet<>();
        private double minimumOrderValue = 0;
        private LocalDateTime expiryDate = null;
        private int numberOfAllowedUsages = Integer.MAX_VALUE;
        private boolean stackable = true;
        private boolean loyaltyOnly = false;

        public Builder(String couponCode, DiscountStrategy discountStrategy) {
            this.couponCode = couponCode;
            this.discountStrategy = discountStrategy;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder scope(CouponScope scope) {
            this.scope = scope;
            return this;
        }

        public Builder applicableProductIds(Set<String> productIds) {
            this.applicableProductIds = productIds;
            return this;
        }

        public Builder minimumOrderValue(double minimumOrderValue) {
            this.minimumOrderValue = minimumOrderValue;
            return this;
        }

        public Builder expiryDate(LocalDateTime expiryDate) {
            this.expiryDate = expiryDate;
            return this;
        }

        public Builder numberOfAllowedUsages(int usages) {
            this.numberOfAllowedUsages = usages;
            return this;
        }

        public Builder stackable(boolean stackable) {
            this.stackable = stackable;
            return this;
        }

        public Builder loyaltyOnly(boolean loyaltyOnly) {
            this.loyaltyOnly = loyaltyOnly;
            return this;
        }

        public Coupon build() {
            if (couponCode == null || couponCode.isBlank()) {
                throw new IllegalArgumentException("Coupon code cannot be null or blank.");
            }
            if (discountStrategy == null) {
                throw new IllegalArgumentException("Discount strategy cannot be null.");
            }
            if (scope == CouponScope.PRODUCT_LEVEL && applicableProductIds.isEmpty()) {
                throw new IllegalArgumentException("Product-level coupon must have applicable product IDs.");
            }
            return new Coupon(this);
        }
    }
}

// ========================== COUPON FACTORY (Singleton) ==========================

class CouponFactory {
    private static volatile CouponFactory instance;

    private CouponFactory() {}

    public static CouponFactory getInstance() {
        if (instance == null) {
            synchronized (CouponFactory.class) {
                if (instance == null) {
                    instance = new CouponFactory();
                }
            }
        }
        return instance;
    }

    /**
     * Creates a Coupon using the given type and metadata.
     * Metadata keys: flatAmount, percentage, maxCap, scope, applicableProductIds,
     *                minimumOrderValue, expiryDate, numberOfAllowedUsages, stackable, loyaltyOnly
     */
    public Coupon createCoupon(String code, CouponType type, Map<String, Object> metadata) {
        DiscountStrategy strategy = createStrategy(type, metadata);
        String description = strategy.getDescription();

        Coupon.Builder builder = new Coupon.Builder(code, strategy)
                .description(description);

        if (metadata.containsKey("scope")) {
            builder.scope((CouponScope) metadata.get("scope"));
        }
        if (metadata.containsKey("applicableProductIds")) {
            @SuppressWarnings("unchecked")
            Set<String> productIds = (Set<String>) metadata.get("applicableProductIds");
            builder.applicableProductIds(productIds);
        }
        if (metadata.containsKey("minimumOrderValue")) {
            builder.minimumOrderValue((Double) metadata.get("minimumOrderValue"));
        }
        if (metadata.containsKey("expiryDate")) {
            builder.expiryDate((LocalDateTime) metadata.get("expiryDate"));
        }
        if (metadata.containsKey("numberOfAllowedUsages")) {
            builder.numberOfAllowedUsages((Integer) metadata.get("numberOfAllowedUsages"));
        }
        if (metadata.containsKey("stackable")) {
            builder.stackable((Boolean) metadata.get("stackable"));
        }
        if (metadata.containsKey("loyaltyOnly")) {
            builder.loyaltyOnly((Boolean) metadata.get("loyaltyOnly"));
        }

        return builder.build();
    }

    private DiscountStrategy createStrategy(CouponType type, Map<String, Object> metadata) {
        switch (type) {
            case FLAT:
                double flatAmount = (Double) metadata.get("flatAmount");
                return new FlatDiscountStrategy(flatAmount);

            case PERCENTAGE:
                double percentage = (Double) metadata.get("percentage");
                return new PercentageDiscountStrategy(percentage);

            case PERCENTAGE_WITH_CAP:
                double pct = (Double) metadata.get("percentage");
                double maxCap = (Double) metadata.get("maxCap");
                return new PercentageWithCapDiscountStrategy(pct, maxCap);

            default:
                throw new IllegalArgumentException("Unknown coupon type: " + type);
        }
    }
}

// ========================== COUPON MANAGER (Singleton) ==========================

class CouponManager {
    private static volatile CouponManager instance;
    private final Map<String, Coupon> couponRegistry;

    private CouponManager() {
        this.couponRegistry = new ConcurrentHashMap<>();
    }

    public static CouponManager getInstance() {
        if (instance == null) {
            synchronized (CouponManager.class) {
                if (instance == null) {
                    instance = new CouponManager();
                }
            }
        }
        return instance;
    }

    public void addCoupon(Coupon coupon) {
        couponRegistry.put(coupon.getCouponCode(), coupon);
        System.out.println("[CouponManager] Registered: " + coupon);
    }

    public void removeCoupon(String couponCode) {
        Coupon removed = couponRegistry.remove(couponCode);
        if (removed != null) {
            System.out.println("[CouponManager] Removed coupon: " + couponCode);
        }
    }

    public Coupon getCoupon(String couponCode) {
        return couponRegistry.get(couponCode);
    }

    public List<Coupon> getAllCoupons() {
        return new ArrayList<>(couponRegistry.values());
    }
}

// ========================== SERVICE (Orchestrator) ==========================

class DiscountCouponEngineService {
    private final CouponManager couponManager;
    private final CouponFactory couponFactory;

    public DiscountCouponEngineService() {
        this.couponManager = CouponManager.getInstance();
        this.couponFactory = CouponFactory.getInstance();
    }

    public Coupon createCoupon(String code, CouponType type, Map<String, Object> metadata) {
        Coupon coupon = couponFactory.createCoupon(code, type, metadata);
        couponManager.addCoupon(coupon);
        return coupon;
    }

    public boolean applyCouponToCart(String couponCode, Cart cart, User user) {
        Coupon coupon = couponManager.getCoupon(couponCode);
        if (coupon == null) {
            System.out.println("  [Service] Coupon '" + couponCode + "' not found.");
            return false;
        }

        System.out.println("  [Service] Attempting to apply coupon: " + couponCode);

        if (!coupon.isApplicable(cart, user)) {
            System.out.println("  [Service] Coupon '" + couponCode + "' is NOT applicable.");
            return false;
        }

        if (!coupon.consumeUsage()) {
            System.out.println("  [Service] Coupon '" + couponCode + "' — no usages left (race condition caught).");
            return false;
        }

        cart.addAppliedCoupon(coupon);
        double discount = coupon.applyDiscount(cart);
        System.out.println("  [Service] ✓ Coupon '" + couponCode + "' applied! Discount: ₹" + String.format("%.2f", discount));
        return true;
    }

    public void removeCouponFromCart(String couponCode, Cart cart) {
        Coupon coupon = couponManager.getCoupon(couponCode);
        if (coupon != null) {
            cart.removeAppliedCoupon(coupon);
            System.out.println("  [Service] Removed coupon '" + couponCode + "' from cart.");
        }
    }

    public double getCartFinalPrice(Cart cart) {
        return cart.getFinalPrice();
    }

    public void displayPriceBreakdown(Cart cart) {
        double totalAmount = cart.getTotalAmount();
        double totalDiscount = 0;

        System.out.println("  ---- Price Breakdown ----");
        System.out.println("  Subtotal: ₹" + String.format("%.2f", totalAmount));

        for (Coupon coupon : cart.getAppliedCoupons()) {
            double discount = coupon.applyDiscount(cart);
            totalDiscount += discount;
            String scope = coupon.getScope() == CouponScope.PRODUCT_LEVEL ? " [product-level]" : "";
            System.out.println("  " + coupon.getCouponCode() + " (" + coupon.getDescription() + ")" + scope
                    + ": -₹" + String.format("%.2f", discount));
        }

        double finalPrice = Math.max(0, totalAmount - totalDiscount);
        System.out.println("  -------------------------");
        System.out.println("  Total Discount: ₹" + String.format("%.2f", totalDiscount));
        System.out.println("  Final Price: ₹" + String.format("%.2f", finalPrice));
        System.out.println("  -------------------------");
    }
}

// ========================== CONTROLLER ==========================

class DiscountCouponEngineController {
    private final DiscountCouponEngineService service;

    public DiscountCouponEngineController() {
        this.service = new DiscountCouponEngineService();
    }

    public Coupon createCoupon(String code, CouponType type, Map<String, Object> metadata) {
        return service.createCoupon(code, type, metadata);
    }

    public boolean applyCoupon(String couponCode, Cart cart, User user) {
        return service.applyCouponToCart(couponCode, cart, user);
    }

    public void removeCoupon(String couponCode, Cart cart) {
        service.removeCouponFromCart(couponCode, cart);
    }

    public double getFinalPrice(Cart cart) {
        return service.getCartFinalPrice(cart);
    }

    public void displayPriceBreakdown(Cart cart) {
        service.displayPriceBreakdown(cart);
    }
}

// ========================== CLIENT (Main Demo) ==========================

public class DiscountCouponEngine {

    public static void main(String[] args) {
        System.out.println("==========================================================");
        System.out.println("         DISCOUNT COUPON ENGINE - Demo                     ");
        System.out.println("==========================================================\n");

        DiscountCouponEngineController controller = new DiscountCouponEngineController();

        // --- Setup Products ---
        Product laptop = new Product("Laptop", 50000.0);
        Product headphones = new Product("Wireless Headphones", 3000.0);
        Product mouse = new Product("Gaming Mouse", 1500.0);
        Product keyboard = new Product("Mechanical Keyboard", 5000.0);
        Product usbCable = new Product("USB Cable", 200.0);

        System.out.println("--- Products ---");
        System.out.println("  " + laptop);
        System.out.println("  " + headphones);
        System.out.println("  " + mouse);
        System.out.println("  " + keyboard);
        System.out.println("  " + usbCable);

        // --- Create Coupons ---
        System.out.println("\n--- Creating Coupons ---");

        // 1. Flat ₹500 off on cart (min order ₹2000, stackable)
        Map<String, Object> flat500Meta = new HashMap<>();
        flat500Meta.put("flatAmount", 500.0);
        flat500Meta.put("minimumOrderValue", 2000.0);
        flat500Meta.put("stackable", true);
        controller.createCoupon("FLAT500", CouponType.FLAT, flat500Meta);

        // 2. 10% off on entire cart (non-stackable, no min order)
        Map<String, Object> pct10Meta = new HashMap<>();
        pct10Meta.put("percentage", 10.0);
        pct10Meta.put("stackable", false);
        controller.createCoupon("SAVE10", CouponType.PERCENTAGE, pct10Meta);

        // 3. 20% off with max cap ₹1000 (stackable, min order ₹5000)
        Map<String, Object> pct20CapMeta = new HashMap<>();
        pct20CapMeta.put("percentage", 20.0);
        pct20CapMeta.put("maxCap", 1000.0);
        pct20CapMeta.put("minimumOrderValue", 5000.0);
        pct20CapMeta.put("stackable", true);
        controller.createCoupon("MEGA20", CouponType.PERCENTAGE_WITH_CAP, pct20CapMeta);

        // 4. Product-level: ₹300 off on headphones only (stackable)
        Map<String, Object> productCouponMeta = new HashMap<>();
        productCouponMeta.put("flatAmount", 300.0);
        productCouponMeta.put("scope", CouponScope.PRODUCT_LEVEL);
        productCouponMeta.put("applicableProductIds", new HashSet<>(Set.of(headphones.getId())));
        productCouponMeta.put("stackable", true);
        controller.createCoupon("AUDIO300", CouponType.FLAT, productCouponMeta);

        // 5. Loyalty-only coupon: 15% off, max ₹2000
        Map<String, Object> loyaltyMeta = new HashMap<>();
        loyaltyMeta.put("percentage", 15.0);
        loyaltyMeta.put("maxCap", 2000.0);
        loyaltyMeta.put("loyaltyOnly", true);
        loyaltyMeta.put("stackable", true);
        controller.createCoupon("LOYAL15", CouponType.PERCENTAGE_WITH_CAP, loyaltyMeta);

        // 6. Limited usage coupon: first 4 users only, flat ₹1000 off
        Map<String, Object> limitedMeta = new HashMap<>();
        limitedMeta.put("flatAmount", 1000.0);
        limitedMeta.put("numberOfAllowedUsages", 4);
        limitedMeta.put("minimumOrderValue", 3000.0);
        limitedMeta.put("stackable", true);
        controller.createCoupon("FIRST4K", CouponType.FLAT, limitedMeta);

        // ========================== DEMO 1: Cart-level Flat Discount ==========================
        System.out.println("\n\n========== DEMO 1: Cart-Level Flat Discount ==========");
        User alice = new User("Alice", "123 MG Road", false);
        alice.getCart().addProduct(laptop);
        alice.getCart().addProduct(headphones);
        alice.getCart().addProduct(mouse);
        System.out.println("\n" + alice + "'s cart:");
        alice.getCart().displayCart();

        System.out.println("\nApplying FLAT500:");
        controller.applyCoupon("FLAT500", alice.getCart(), alice);
        controller.displayPriceBreakdown(alice.getCart());

        // ========================== DEMO 2: Percentage With Cap ==========================
        System.out.println("\n\n========== DEMO 2: Percentage With Cap ==========");
        User bob = new User("Bob", "456 Park Street", false);
        bob.getCart().addProduct(laptop);
        bob.getCart().addProduct(keyboard);
        System.out.println("\n" + bob + "'s cart:");
        bob.getCart().displayCart();

        System.out.println("\nApplying MEGA20 (20% off, max ₹1000):");
        controller.applyCoupon("MEGA20", bob.getCart(), bob);
        controller.displayPriceBreakdown(bob.getCart());

        // ========================== DEMO 3: Product-Level Discount ==========================
        System.out.println("\n\n========== DEMO 3: Product-Level Discount ==========");
        User carol = new User("Carol", "789 Lake View", false);
        carol.getCart().addProduct(headphones);
        carol.getCart().addProduct(mouse);
        carol.getCart().addProduct(usbCable);
        System.out.println("\n" + carol + "'s cart:");
        carol.getCart().displayCart();

        System.out.println("\nApplying AUDIO300 (₹300 off on headphones only):");
        controller.applyCoupon("AUDIO300", carol.getCart(), carol);
        controller.displayPriceBreakdown(carol.getCart());

        // ========================== DEMO 4: Stacking Coupons ==========================
        System.out.println("\n\n========== DEMO 4: Stacking Multiple Coupons ==========");
        User dave = new User("Dave", "321 Hill Road", true);
        dave.getCart().addProduct(laptop);
        dave.getCart().addProduct(headphones);
        dave.getCart().addProduct(keyboard);
        System.out.println("\n" + dave + "'s cart:");
        dave.getCart().displayCart();

        System.out.println("\nApplying FLAT500 (stackable):");
        controller.applyCoupon("FLAT500", dave.getCart(), dave);

        System.out.println("\nApplying AUDIO300 (stackable, product-level):");
        controller.applyCoupon("AUDIO300", dave.getCart(), dave);

        System.out.println("\nApplying LOYAL15 (loyalty-only, stackable):");
        controller.applyCoupon("LOYAL15", dave.getCart(), dave);

        System.out.println("\nFinal breakdown with stacked coupons:");
        controller.displayPriceBreakdown(dave.getCart());

        // ========================== DEMO 5: Non-Stackable Rejection ==========================
        System.out.println("\n\n========== DEMO 5: Non-Stackable Coupon Rejection ==========");

        // Scenario A: Try non-stackable AFTER stackable
        User eve = new User("Eve", "555 Main Street", false);
        eve.getCart().addProduct(laptop);
        eve.getCart().addProduct(headphones);
        System.out.println("\nScenario A - " + eve + "'s cart:");
        eve.getCart().displayCart();

        System.out.println("\nApplying FLAT500 (stackable) first:");
        controller.applyCoupon("FLAT500", eve.getCart(), eve);

        System.out.println("\nTrying SAVE10 (non-stackable) on top:");
        controller.applyCoupon("SAVE10", eve.getCart(), eve);
        System.out.println("  → Rejected: non-stackable cannot be added when others exist.");

        // Scenario B: Try stackable AFTER non-stackable
        System.out.println("\nScenario B - Fresh cart:");
        User frank = new User("Frank", "555 Main Street", false);
        frank.getCart().addProduct(laptop);
        frank.getCart().addProduct(headphones);

        System.out.println("\nApplying SAVE10 (non-stackable) first:");
        controller.applyCoupon("SAVE10", frank.getCart(), frank);

        System.out.println("\nTrying FLAT500 (stackable) on top of non-stackable:");
        controller.applyCoupon("FLAT500", frank.getCart(), frank);
        System.out.println("  → Rejected: cannot stack on top of a non-stackable coupon.");

        controller.displayPriceBreakdown(frank.getCart());

        // ========================== DEMO 6: Loyalty-Only Coupon ==========================
        System.out.println("\n\n========== DEMO 6: Loyalty-Only Coupon ==========");

        User nonLoyalUser = new User("Henry", "999 Oak Avenue", false);
        nonLoyalUser.getCart().addProduct(laptop);
        System.out.println("\n" + nonLoyalUser + " (non-loyalty) trying LOYAL15:");
        controller.applyCoupon("LOYAL15", nonLoyalUser.getCart(), nonLoyalUser);

        User loyalUser = new User("Grace", "888 Pine Lane", true);
        loyalUser.getCart().addProduct(laptop);
        System.out.println("\n" + loyalUser + " (loyalty) trying LOYAL15:");
        controller.applyCoupon("LOYAL15", loyalUser.getCart(), loyalUser);
        controller.displayPriceBreakdown(loyalUser.getCart());

        // ========================== DEMO 7: Minimum Order Value ==========================
        System.out.println("\n\n========== DEMO 7: Minimum Order Value Check ==========");
        User hank = new User("Hank", "111 River Rd", false);
        hank.getCart().addProduct(usbCable);
        System.out.println("\n" + hank + "'s cart (₹200 only):");
        hank.getCart().displayCart();

        System.out.println("\nTrying FLAT500 (min order ₹2000):");
        controller.applyCoupon("FLAT500", hank.getCart(), hank);
        System.out.println("  → Rejected: cart below minimum order value.");

        // ========================== DEMO 8: Thread-Safe Limited Usages ==========================
        System.out.println("\n\n========== DEMO 8: Thread-Safe Limited Usages (First 4 Only) ==========");
        System.out.println("Simulating 8 users trying to use coupon FIRST4K simultaneously...\n");

        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(8);
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 1; i <= 8; i++) {
            final int userId = i;
            executor.submit(() -> {
                try {
                    startGate.await(); // all threads start together
                    User threadUser = new User("ThreadUser-" + userId, "Addr-" + userId, false);
                    threadUser.getCart().addProduct(new Product("Item-" + userId, 5000.0));

                    DiscountCouponEngineService threadService = new DiscountCouponEngineService();
                    boolean success = threadService.applyCouponToCart("FIRST4K", threadUser.getCart(), threadUser);

                    if (success) {
                        results.add("  User-" + userId + ": ✓ SUCCESS (got the discount!)");
                    } else {
                        results.add("  User-" + userId + ": ✗ FAILED  (coupon exhausted)");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown(); // release all threads simultaneously
        try {
            doneLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdown();

        System.out.println("\n--- Thread Race Results ---");
        results.stream().sorted().forEach(System.out::println);

        Coupon first4k = CouponManager.getInstance().getCoupon("FIRST4K");
        System.out.println("\n  Remaining usages for FIRST4K: " + first4k.getRemainingUsages());
        System.out.println("  (Expected: exactly 4 successes, 4 failures)");

        // ========================== DONE ==========================
        System.out.println("\n\n==========================================================");
        System.out.println("                      Demo Complete                        ");
        System.out.println("==========================================================");
    }
}
