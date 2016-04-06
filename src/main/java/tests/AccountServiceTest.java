package tests;

import com.github.javafaker.Faker;
import main.AccountServiceDBImpl;
import main.UserProfile;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;

/**
 * Created by vladislav on 06.04.16.
 */
public class AccountServiceTest extends Assert {

    Faker faker;
    AccountServiceDBImpl accountService;

    @Before
    public void setUp() {
        faker = new Faker();
        accountService = new AccountServiceDBImpl();
        try {
            accountService.initialize();
        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    @After
    public void tearDown() {
        try {
            accountService.close();
        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Test
    public void testAddUser() {
        UserProfile userProfile = new UserProfile(faker.name().firstName(), faker.name().lastName(), faker.internet().emailAddress());
        assertNotNull(userProfile);
    }

    @Test
    public void testGetUser() {
        long id = 1;
        UserProfile userProfile = accountService.getUser(id);
        assertNotNull(userProfile);
        assertEquals("admin", userProfile.getLogin());
        assertEquals("admin", userProfile.getPassword());
    }

    @Test
    public void testEditUser() {
        long id = 1;
        UserProfile userProfile = accountService.getUser(id);
        assertNotNull(userProfile);
        UserProfile userProfile1 = new UserProfile("admin1", "admin1", "admin1@admin.com");
        assertTrue(accountService.editUser(userProfile, userProfile1));
        userProfile1 = new UserProfile("admin", "admin", "admin@admin.com");
        assertTrue(accountService.editUser(userProfile, userProfile1));
    }

    @Test
    public void testDeleteUser() {
        long id = 1;
        assertTrue(accountService.deleteUser(id));
    }

    @Test
    public void testLogin() {
        long id = 1;
        UserProfile userProfile = accountService.getUser(id);
        assertNotNull(userProfile);
        String hash = "12345";
        assertTrue(accountService.login(hash, userProfile));
    }

    @Test
    public void testLogout() {
        testLogin();
        String hash = "12345";
        assertTrue(accountService.logout(hash));
    }

    @Test
    public void testLoggedIn() {
        testLogin();
        String hash = "12345";
        assertTrue(accountService.isLoggedIn(hash));
    }

    @Test
    public void testGetUserBySession() {
        testLogin();
        String hash = "12345";
        UserProfile userProfile = accountService.getUserBySession(hash);
        assertNotNull(userProfile);
        assertEquals("admin", userProfile.getLogin());
        assertEquals("admin", userProfile.getPassword());
    }

    @Test
    public void testGetUserByLogin() {
        testLogin();
        String login = "admin";
        UserProfile userProfile = accountService.getUserByLogin(login);
        assertNotNull(userProfile);
        assertEquals(login, userProfile.getLogin());
        assertEquals(login, userProfile.getPassword());
    }
}
