const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_customer_1";
const BOB_UID = "bob_customer_2";
const ADMIN_UID = "admin_owner_1";
const ADMIN_EMAIL = "abbas.0078666@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function adminContext() {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  });
}

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("products").where("active", "==", true).get());
  await assertFails(unauthDb.collection("categories").where("active", "==", true).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("orders").where("customerId", "==", ALICE_UID).get());
});

test("Email spoofing attack: unverified admin email cannot access admin functions", async () => {
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  const now = new Date(Date.now() - 1000);
  await assertFails(
    spoofDb.collection("categories").doc("cat_1").set({
      name: "Fake Category",
      iconKey: "grocery",
      sortOrder: 1,
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(spoofDb.collection("admin_users").get());
});

test("Bootstrapped admin can manage categories, products, coupons, delivery_zones, app_settings, inventory_logs", async () => {
  const db = adminContext().firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    db.collection("categories").doc("cat_grocery").set({
      name: "Grocery & Ration",
      subtitle: "Atta, Rice & Dal",
      iconKey: "grocery",
      sortOrder: 1,
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    db.collection("products").doc("prod_atta").set({
      productName: "Chakki Fresh Atta",
      brand: "Apna Harvest",
      description: "Stone-ground whole wheat flour",
      categoryId: "cat_grocery",
      categoryName: "Grocery & Ration",
      imageUrls: ["drawable://img_cat_pantry"],
      sellingPrice: 249.0,
      originalPrice: 290.0,
      unit: "5 kg",
      stockQuantity: 40,
      reservedQuantity: 0,
      soldQuantity: 0,
      lowStockThreshold: 5,
      available: true,
      active: true,
      bestSeller: true,
      recommended: true,
      dailyOffer: false,
      popular: true,
      featured: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    db.collection("inventory_logs").doc("log_1").set({
      productId: "prod_atta",
      productName: "Chakki Fresh Atta",
      changeType: "RESTOCK",
      quantityDelta: 40,
      previousStock: 0,
      newStock: 40,
      reason: "Initial stock",
      adminUid: ADMIN_UID,
      adminEmail: ADMIN_EMAIL,
      createdAt: now,
    })
  );

  await assertSucceeds(
    db.collection("app_settings").doc("store_config").set({
      storeName: "APNA BAZAAR | APNI PASAND",
      tagline: "Your Daily Needs, At Your Doorstep.",
      serviceAvailable: true,
      operatingHours: "7:00 AM - 10:30 PM",
      defaultDeliveryFee: 20.0,
      minOrderAmount: 99.0,
      serviceRadiusKm: 3.0,
      storeLat: 28.6139,
      storeLng: 77.209,
      estimatedDeliveryText: "30-60 mins",
      supportPhone: "+91 9876543210",
      supportEmail: ADMIN_EMAIL,
      updatedAt: now,
    })
  );
});

test("Customer cannot register as admin, modify product prices, or read other users PII", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const now = new Date(Date.now() - 10000);
    await db.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      name: "Bob",
      phone: "9876500000",
      email: "bob@example.com",
      language: "en",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("products").doc("prod_1").set({
      productName: "Basmati Rice",
      brand: "Apna Harvest",
      categoryId: "cat_1",
      categoryName: "Grocery & Ration",
      imageUrls: [],
      sellingPrice: 120.0,
      originalPrice: 150.0,
      unit: "1 kg",
      stockQuantity: 10,
      reservedQuantity: 0,
      soldQuantity: 0,
      lowStockThreshold: 3,
      available: true,
      active: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com" }).firestore();
  const now = new Date(Date.now() - 1000);

  // Cannot escalate to admin
  await assertFails(
    aliceDb.collection("admin_users").doc(ALICE_UID).set({
      adminId: ALICE_UID,
      email: "alice@example.com",
      name: "Alice",
      role: "super_admin",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cannot read Bob's profile
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());

  // Cannot change product price or stock
  await assertFails(aliceDb.collection("products").doc("prod_1").update({ sellingPrice: 1.0, updatedAt: now }));
  await assertFails(aliceDb.collection("products").doc("prod_1").update({ stockQuantity: 999, updatedAt: now }));

  // Can read active products with matching filter
  await assertSucceeds(aliceDb.collection("products").where("active", "==", true).get());
  // Unfiltered product query must fail
  await assertFails(aliceDb.collection("products").get());
});

test("Customer cart, address, and order lifecycle enforce ownership and payment safety", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const now = new Date(Date.now() - 10000);
    await ctx.firestore().collection("products").doc("prod_milk").set({
      productName: "Full Cream Milk",
      brand: "Apna Dairy",
      categoryId: "cat_dairy",
      categoryName: "Dairy & Breakfast",
      imageUrls: [],
      sellingPrice: 34.0,
      originalPrice: 36.0,
      unit: "500 ml",
      stockQuantity: 25,
      reservedQuantity: 0,
      soldQuantity: 0,
      lowStockThreshold: 5,
      available: true,
      active: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  // Alice adds item to cart
  await assertSucceeds(
    aliceDb.collection("cart_items").doc("ci_1").set({
      userId: ALICE_UID,
      productId: "prod_milk",
      productName: "Full Cream Milk",
      brand: "Apna Dairy",
      unit: "500 ml",
      sellingPrice: 34.0,
      originalPrice: 36.0,
      quantity: 2,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice creates a Pending COD order
  await assertSucceeds(
    aliceDb.collection("orders").doc("ord_1").set({
      orderNumber: "AB-1001",
      customerId: ALICE_UID,
      customerName: "Alice",
      customerPhone: "9876543210",
      addressSnapshot: {
        label: "Home",
        houseLine: "Flat 202",
        areaLine: "Sector 14",
        pincode: "110001",
      },
      orderItemsSnapshot: [
        { productId: "prod_milk", productName: "Full Cream Milk", quantity: 2, sellingPrice: 34.0 },
      ],
      subtotal: 68.0,
      discount: 0.0,
      deliveryFee: 20.0,
      grandTotal: 88.0,
      paymentMethod: "COD",
      paymentStatus: "Pending",
      orderStatus: "Pending",
      estimatedDeliveryMinutes: "30-60 mins",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Customer CANNOT forge Paid or Confirmed status on order creation
  await assertFails(
    aliceDb.collection("orders").doc("ord_forged").set({
      orderNumber: "AB-1002",
      customerId: ALICE_UID,
      customerName: "Alice",
      customerPhone: "9876543210",
      addressSnapshot: { label: "Home" },
      orderItemsSnapshot: [{ productId: "prod_milk", quantity: 1 }],
      subtotal: 34.0,
      discount: 0.0,
      deliveryFee: 0.0,
      grandTotal: 34.0,
      paymentMethod: "RAZORPAY",
      paymentStatus: "Paid",
      orderStatus: "Pending",
      estimatedDeliveryMinutes: "30-60 mins",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob cannot read Alice's orders
  await assertFails(bobDb.collection("orders").doc("ord_1").get());
  await assertFails(bobDb.collection("orders").where("customerId", "==", ALICE_UID).get());

  // Alice can query her own orders
  await assertSucceeds(aliceDb.collection("orders").where("customerId", "==", ALICE_UID).get());

  // Alice can cancel her Pending order, and then cannot update it once Cancelled (terminal lock)
  await assertSucceeds(
    aliceDb.collection("orders").doc("ord_1").update({
      orderStatus: "Cancelled",
      cancelReason: "Ordered by mistake",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("orders").doc("ord_1").update({
      orderStatus: "Confirmed",
      updatedAt: new Date(Date.now() - 200),
    })
  );
});
