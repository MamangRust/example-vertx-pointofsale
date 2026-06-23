package com.sanedge.example_crud.seeder;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.example_crud.model.Merchant;
import com.sanedge.example_crud.model.Product;
import com.sanedge.example_crud.model.Role;
import com.sanedge.example_crud.model.User;
import com.sanedge.example_crud.model.cashier.Cashier;
import com.sanedge.example_crud.model.category.Category;
import com.sanedge.example_crud.model.order.Order;

import at.favre.lib.crypto.bcrypt.BCrypt;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

public class DatabaseSeeder {
  private static final Logger logger = LoggerFactory.getLogger(DatabaseSeeder.class);

  private final Pool pool;

  public DatabaseSeeder(Pool pool) {
    this.pool = pool;
  }

  public Future<Void> seed() {
    logger.info("🌱 Starting database seeding...");

    // Check if there are roles already
    return pool.preparedQuery("SELECT COUNT(*) as count FROM roles").execute()
        .compose(rows -> {
          long count = rows.iterator().next().getLong("count");
          if (count > 0) {
            logger.info("📊 Database already contains data, skipping seeding");
            return Future.succeededFuture();
          }

          return seedRoles()
              .compose(roles -> {
                logger.info("✅ Roles seeded successfully");
                return seedUsers()
                    .compose(users -> {
                      logger.info("✅ Users seeded successfully");
                      return seedUserRoles(users, roles)
                          .compose(v -> seedMerchants(users))
                          .compose(merchants -> {
                            logger.info("✅ Merchants seeded successfully");
                            return seedCashiers(merchants, users)
                                .compose(cashiers -> {
                                  logger.info("✅ Cashiers seeded successfully");
                                  return seedCategories()
                                      .compose(categories -> {
                                        logger.info("✅ Categories seeded successfully");
                                        return seedProducts(merchants, categories)
                                            .compose(products -> {
                                              logger.info("✅ Products seeded successfully");
                                              return seedOrders(merchants, cashiers)
                                                  .compose(orders -> {
                                                    logger.info("✅ Orders seeded successfully");
                                                    return seedOrderItems(orders, products)
                                                        .compose(v2 -> seedTransactions(orders, merchants));
                                                  });
                                            });
                                      });
                                });
                          });
                    });
              })
              .onSuccess(v -> logger.info("✅ Database seeding completed successfully"))
              .onFailure(err -> logger.error("❌ Database seeding failed: {}", err.getMessage(), err));
        }).mapEmpty();
  }

  private Future<List<Role>> seedRoles() {
    logger.info("🔧 Seeding roles...");
    List<Role> roles = new ArrayList<>();

    return pool.preparedQuery("INSERT INTO roles (role_name) VALUES ($1) RETURNING role_id, role_name")
        .execute(Tuple.of("ADMIN"))
        .compose(rows -> {
          Row r = rows.iterator().next();
          roles.add(Role.builder().roleId(r.getInteger("role_id")).roleName(r.getString("role_name")).build());
          return pool.preparedQuery("INSERT INTO roles (role_name) VALUES ($1) RETURNING role_id, role_name")
              .execute(Tuple.of("USER"));
        })
        .compose(rows -> {
          Row r = rows.iterator().next();
          roles.add(Role.builder().roleId(r.getInteger("role_id")).roleName(r.getString("role_name")).build());
          return pool.preparedQuery("INSERT INTO roles (role_name) VALUES ($1) RETURNING role_id, role_name")
              .execute(Tuple.of("MANAGER"));
        })
        .map(rows -> {
          Row r = rows.iterator().next();
          roles.add(Role.builder().roleId(r.getInteger("role_id")).roleName(r.getString("role_name")).build());
          return roles;
        });
  }

  private Future<List<User>> seedUsers() {
    logger.info("👥 Seeding users...");
    List<User> users = new ArrayList<>();

    String adminPass = hashPassword("admin123");
    String userPass = hashPassword("user123");
    String managerPass = hashPassword("manager123");

    return pool
        .preparedQuery(
            "INSERT INTO users (firstname, lastname, email, password) VALUES ($1, $2, $3, $4) RETURNING user_id, email")
        .execute(Tuple.of("John", "Doe", "admin@example.com", adminPass))
        .compose(rows -> {
          Row r = rows.iterator().next();
          users.add(User.builder().userId(r.getInteger("user_id")).email(r.getString("email")).build());
          return pool.preparedQuery(
              "INSERT INTO users (firstname, lastname, email, password) VALUES ($1, $2, $3, $4) RETURNING user_id, email")
              .execute(Tuple.of("Jane", "Smith", "user@example.com", userPass));
        })
        .compose(rows -> {
          Row r = rows.iterator().next();
          users.add(User.builder().userId(r.getInteger("user_id")).email(r.getString("email")).build());
          return pool.preparedQuery(
              "INSERT INTO users (firstname, lastname, email, password) VALUES ($1, $2, $3, $4) RETURNING user_id, email")
              .execute(Tuple.of("Bob", "Johnson", "manager@example.com", managerPass));
        })
        .map(rows -> {
          Row r = rows.iterator().next();
          users.add(User.builder().userId(r.getInteger("user_id")).email(r.getString("email")).build());
          return users;
        });
  }

  private Future<Void> seedUserRoles(List<User> users, List<Role> roles) {
    logger.info("🔗 Assigning roles to users...");
    return pool.preparedQuery("INSERT INTO user_roles (user_id, role_id) VALUES ($1, $2)")
        .execute(Tuple.of(users.get(0).getUserId(), roles.get(0).getRoleId()))
        .compose(v -> pool.preparedQuery("INSERT INTO user_roles (user_id, role_id) VALUES ($1, $2)")
            .execute(Tuple.of(users.get(1).getUserId(), roles.get(1).getRoleId())))
        .compose(v -> pool.preparedQuery("INSERT INTO user_roles (user_id, role_id) VALUES ($1, $2)")
            .execute(Tuple.of(users.get(2).getUserId(), roles.get(2).getRoleId())))
        .mapEmpty();
  }

  private Future<List<Merchant>> seedMerchants(List<User> users) {
    logger.info("🏪 Seeding merchants...");
    List<Merchant> merchants = new ArrayList<>();

    return pool.preparedQuery(
        "INSERT INTO merchants (user_id, name, description, address, contact_email, contact_phone, status) VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING merchant_id, name")
        .execute(Tuple.of(users.get(0).getUserId(), "Admin Shop", "General store", "Admin Street 1",
            "admin.shop@example.com", "0812345678", "success"))
        .compose(rows -> {
          Row r = rows.iterator().next();
          merchants.add(Merchant.builder().merchantId(r.getLong("merchant_id")).name(r.getString("name")).build());
          return pool.preparedQuery(
              "INSERT INTO merchants (user_id, name, description, address, contact_email, contact_phone, status) VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING merchant_id, name")
              .execute(Tuple.of(users.get(2).getUserId(), "Manager Shop", "Manager retail outlet",
                  "Manager Boulevard 2", "manager.shop@example.com", "0898765432", "success"));
        })
        .map(rows -> {
          Row r = rows.iterator().next();
          merchants.add(Merchant.builder().merchantId(r.getLong("merchant_id")).name(r.getString("name")).build());
          return merchants;
        });
  }

  private Future<List<Cashier>> seedCashiers(List<Merchant> merchants, List<User> users) {
    logger.info("💸 Seeding cashiers...");
    List<Cashier> cashiers = new ArrayList<>();

    return pool
        .preparedQuery(
            "INSERT INTO cashiers (merchant_id, user_id, name) VALUES ($1, $2, $3) RETURNING cashier_id, name")
        .execute(Tuple.of(merchants.get(0).getMerchantId(), users.get(0).getUserId(), "John Cashier"))
        .compose(rows -> {
          Row r = rows.iterator().next();
          cashiers.add(Cashier.builder().cashierId(r.getLong("cashier_id")).name(r.getString("name")).build());
          return pool
              .preparedQuery(
                  "INSERT INTO cashiers (merchant_id, user_id, name) VALUES ($1, $2, $3) RETURNING cashier_id, name")
              .execute(Tuple.of(merchants.get(1).getMerchantId(), users.get(2).getUserId(), "Bob Cashier"));
        })
        .map(rows -> {
          Row r = rows.iterator().next();
          cashiers.add(Cashier.builder().cashierId(r.getLong("cashier_id")).name(r.getString("name")).build());
          return cashiers;
        });
  }

  private Future<List<Category>> seedCategories() {
    logger.info("🏷️ Seeding categories...");
    List<Category> categories = new ArrayList<>();

    return pool
        .preparedQuery(
            "INSERT INTO categories (name, description, slug_category) VALUES ($1, $2, $3) RETURNING category_id, name")
        .execute(Tuple.of("Food & Beverage", "Delicious meals and drinks", "food-beverage"))
        .compose(rows -> {
          Row r = rows.iterator().next();
          categories.add(Category.builder().categoryId(r.getLong("category_id")).name(r.getString("name")).build());
          return pool.preparedQuery(
              "INSERT INTO categories (name, description, slug_category) VALUES ($1, $2, $3) RETURNING category_id, name")
              .execute(Tuple.of("Electronics", "Gadgets and tech devices", "electronics"));
        })
        .map(rows -> {
          Row r = rows.iterator().next();
          categories.add(Category.builder().categoryId(r.getLong("category_id")).name(r.getString("name")).build());
          return categories;
        });
  }

  private Future<List<Product>> seedProducts(List<Merchant> merchants, List<Category> categories) {
    logger.info("📦 Seeding products...");
    List<Product> products = new ArrayList<>();

    return pool.preparedQuery(
        "INSERT INTO products (merchant_id, category_id, name, description, price, count_in_stock, brand, weight, slug_product, image_product, barcode) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11) RETURNING product_id, name, price")
        .execute(Tuple.of(merchants.get(0).getMerchantId(), categories.get(0).getCategoryId(), "Coffee Beans",
            "Freshly roasted Arabica beans", 15000, 100, "JavaCoffee", 250, "coffee-beans", "coffee.jpg", "1111111111"))
        .compose(rows -> {
          Row r = rows.iterator().next();
          products.add(Product.builder().productId(r.getLong("product_id")).name(r.getString("name"))
              .price(r.getInteger("price")).build());
          return pool.preparedQuery(
              "INSERT INTO products (merchant_id, category_id, name, description, price, count_in_stock, brand, weight, slug_product, image_product, barcode) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11) RETURNING product_id, name, price")
              .execute(Tuple.of(merchants.get(1).getMerchantId(), categories.get(1).getCategoryId(), "Wireless Mouse",
                  "Ergonomic bluetooth mouse", 250000, 50, "Logi", 100, "wireless-mouse", "mouse.jpg", "2222222222"));
        })
        .map(rows -> {
          Row r = rows.iterator().next();
          products.add(Product.builder().productId(r.getLong("product_id")).name(r.getString("name"))
              .price(r.getInteger("price")).build());
          return products;
        });
  }

  private Future<List<Order>> seedOrders(List<Merchant> merchants, List<Cashier> cashiers) {
    logger.info("🛒 Seeding orders...");
    List<Order> orders = new ArrayList<>();

    return pool.preparedQuery(
        "INSERT INTO orders (merchant_id, cashier_id, total_price) VALUES ($1, $2, $3) RETURNING order_id, total_price")
        .execute(Tuple.of(merchants.get(0).getMerchantId(), cashiers.get(0).getCashierId(), 30000L))
        .compose(rows -> {
          Row r = rows.iterator().next();
          orders.add(Order.builder().orderId(r.getLong("order_id")).totalPrice(r.getLong("total_price")).build());
          return pool.preparedQuery(
              "INSERT INTO orders (merchant_id, cashier_id, total_price) VALUES ($1, $2, $3) RETURNING order_id, total_price")
              .execute(Tuple.of(merchants.get(1).getMerchantId(), cashiers.get(1).getCashierId(), 250000L));
        })
        .map(rows -> {
          Row r = rows.iterator().next();
          orders.add(Order.builder().orderId(r.getLong("order_id")).totalPrice(r.getLong("total_price")).build());
          return orders;
        });
  }

  private Future<Void> seedOrderItems(List<Order> orders, List<Product> products) {
    logger.info("🍕 Seeding order items...");
    return pool.preparedQuery("INSERT INTO order_items (order_id, product_id, quantity, price) VALUES ($1, $2, $3, $4)")
        .execute(Tuple.of(orders.get(0).getOrderId(), products.get(0).getProductId(), 2, products.get(0).getPrice()))
        .compose(v -> pool
            .preparedQuery("INSERT INTO order_items (order_id, product_id, quantity, price) VALUES ($1, $2, $3, $4)")
            .execute(
                Tuple.of(orders.get(1).getOrderId(), products.get(1).getProductId(), 1, products.get(1).getPrice())))
        .mapEmpty();
  }

  private Future<Void> seedTransactions(List<Order> orders, List<Merchant> merchants) {
    logger.info("💳 Seeding transactions...");
    return pool.preparedQuery(
        "INSERT INTO transactions (order_id, merchant_id, payment_method, amount, change_amount, payment_status) VALUES ($1, $2, $3, $4, $5, $6)")
        .execute(
            Tuple.of(orders.get(0).getOrderId(), merchants.get(0).getMerchantId(), "CASH", 50000, 20000, "completed"))
        .compose(v -> pool.preparedQuery(
            "INSERT INTO transactions (order_id, merchant_id, payment_method, amount, change_amount, payment_status) VALUES ($1, $2, $3, $4, $5, $6)")
            .execute(
                Tuple.of(orders.get(1).getOrderId(), merchants.get(1).getMerchantId(), "CASH", 250000, 0, "completed")))
        .mapEmpty();
  }

  private String hashPassword(String plainPassword) {
    return BCrypt.withDefaults().hashToString(12, plainPassword.toCharArray());
  }

  public static void runSeeder(
      Pool pool,
      boolean enableSeeder) {

    if (!enableSeeder) {
      logger.info("🚫 Database seeder disabled, skipping");
      return;
    }

    DatabaseSeeder seeder = new DatabaseSeeder(pool);

    seeder.seed()
        .onSuccess(v -> logger.info("🎉 Database seeder executed successfully"))
        .onFailure(err -> logger.error("💥 Database seeder failed: {}", err.getMessage(), err));
  }
}
