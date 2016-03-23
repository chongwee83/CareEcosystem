package com.ucsf.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import com.ucsf.R;
import com.ucsf.core.services.Services;
import com.ucsf.core_phone.ui.widgets.HeaderView;
import com.ucsf.security.AdminInstance;
import com.ucsf.services.StartupService;

import net.hockeyapp.android.FeedbackManager;

/**
 * Starting screen of the application.
 * The only possible interaction is the login button.
 * If the authentication failed, goes back to this screen, therefore a patient
 * cannot go anywhere else.
 *
 * @author  Julien Jacquemot
 * @version 1.0
 */
public class StartScreenActivity extends Activity {
    private HeaderView mHeaderView;

    @Override
    public void onStart() {
        super.onStart();
        StartupService.startServices(this);
        mHeaderView.updateStatusIcon(Services.areServicesEnabled(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        StartupService.startupChecks(this);
        mHeaderView.updateStatusIcon(Services.areServicesEnabled(this));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == AdminInstance.REQUEST_ENABLE_ADMIN_MODE) {
            if (resultCode != Activity.RESULT_OK) {
                AdminInstance.enableAdminMode(this);
            }
        } else
            super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the view
        setContentView(R.layout.start_screen);

        mHeaderView = (HeaderView) findViewById(R.id.header);

        // Setup the login button
        ImageButton loginButton = (ImageButton) findViewById(R.id.button_login);
        loginButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // If we are still login, branch directly to the appropriate screen,
                // otherwise display the login screen.
                FeedbackManager.register(StartScreenActivity.this, StartupService.APP_IDENTIFIER, null);
                FeedbackManager.showFeedbackActivity(StartScreenActivity.this);

                Intent intent = new Intent(StartScreenActivity.this, LoginActivity.class);

                startActivity(intent);
                return true;
            }
        });
    }


}
