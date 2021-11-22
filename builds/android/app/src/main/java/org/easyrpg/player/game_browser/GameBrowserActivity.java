package org.easyrpg.player.game_browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Bundle;
import com.google.android.material.navigation.NavigationView;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.easyrpg.player.R;
import org.easyrpg.player.button_mapping.ButtonMappingManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GameBrowserActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {
    public static Boolean libraryLoaded = false;

    private static final int THUMBNAIL_HORIZONTAL_SIZE_DPI = 290;
    private static Game selectedGame;

    private RecyclerView recyclerView;
    private RecyclerView.LayoutManager layoutManager;
    private int nbOfGamesPerLine;
    private boolean isScanProcessing;
    private static List<Game> displayedGamesList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!libraryLoaded) {
            try {
                System.loadLibrary("gamebrowser");
                libraryLoaded = true;
            } catch (UnsatisfiedLinkError e) {
                Log.e("EasyRPG Player", "Couldn't load libgamebrowser. XYZ parsing will be unavailable: " + e.getMessage());
            }
        }

        setContentView(R.layout.browser_activity);

        // Configure the toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Configure the lateral menu
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        /// Display the game list
        recyclerView = (RecyclerView) findViewById(R.id.game_browser_api15_recycleview);
        recyclerView.setHasFixedSize(true);
        setLayoutManager(this.getResources().getConfiguration());

        // To limit the number of syscalls, we only scan for games at startup and when the user
        // ask to refresh the games list
        if (GameBrowserActivity.displayedGamesList == null) {
            scanAndDisplayGamesList(this);
        } else {
            recyclerView.setAdapter(new MyAdapter(this, GameBrowserActivity.displayedGamesList, nbOfGamesPerLine));
        }
    }

    @Override
    public void onBackPressed() {
        // Open the lateral menu
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.game_browser, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.refresh) {
            scanAndDisplayGamesList(this);
            return true;
        } else if (id == R.id.menu) {
            GameBrowserHelper.openSettingsActivity(this);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_help) {
            GameBrowserHelper.displayHowToUseEasyRpgDialog(this);
        } else if (id == R.id.nav_manage) {
            GameBrowserHelper.openSettingsActivity(this);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * Change the grid depending on the orientation
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        setLayoutManager(newConfig);
        scanAndDisplayGamesList(this);
    }

    public void scanAndDisplayGamesList(Activity activity) {
        resetGamesList();

        // TODO : Make the use of isScanProcessing synchronized (not really useful)
        if (isScanProcessing){
            return;
        }
        isScanProcessing = true;

        // Empty the games list and display a loading message
        ArrayList<String> loadingMessageList = new ArrayList<String>();
        // TODO : Externalize this string
        loadingMessageList.add("Loading...\nYes, Android files listing became really slow.");
        recyclerView.setAdapter(new ErrorAdapter(loadingMessageList, activity));

        // Start the scan asynchronously
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Scan games
                GameScanner gameScanner = GameScanner.getInstance(activity);

                // "Only the original thread that created a view hierarchy can touch its views."
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Populate the list view
                        if (gameScanner.hasError()) {
                            recyclerView.setAdapter(new ErrorAdapter(gameScanner.getErrorList(), activity));
                        } else {
                            List<Game> gameList = gameScanner.getGameList();
                            GameBrowserActivity.displayedGamesList = gameList;
                            recyclerView.setAdapter(new MyAdapter(activity, gameList, nbOfGamesPerLine));
                        }
                        isScanProcessing = false;
                    }
                });
            }
            }).start();
    }

    /**
     * Set the layout manager depending on the screen orientation
     */
    public void setLayoutManager(Configuration configuration) {
        // Determine the layout template (List or Grid, number of element per line for the grid)
        DisplayMetrics displayMetrics = this.getResources().getDisplayMetrics();
        float dpWidth = displayMetrics.widthPixels / displayMetrics.density;
        this.nbOfGamesPerLine = (int)(dpWidth / THUMBNAIL_HORIZONTAL_SIZE_DPI);

        recyclerView.setLayoutManager(new GridLayoutManager(this, nbOfGamesPerLine));
    }

    public static Game getSelectedGame() {
        return selectedGame;
    }

    public static void resetGamesList() {
        GameBrowserActivity.displayedGamesList = null;
    }

    static class MyAdapter extends RecyclerView.Adapter<MyAdapter.ViewHolder> {
        private List<Game> gameList;
        private Activity activity;
        private int nbOfGamesPerLine;

        IniEncodingReader iniReader;

        public MyAdapter(Activity activity, List<Game> gameList, int nbOfGamesPerLine) {
            this.gameList = gameList;
            this.activity = activity;
            this.nbOfGamesPerLine = nbOfGamesPerLine;
        }

        @Override
        public int getItemCount() {
            return gameList.size();
        }

        // Create new views (invoked by the layout manager)
        @Override
        public MyAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());

            // On inflate la vue et on la remplie
            View v;
            if (this.nbOfGamesPerLine <= 1) {
                v = inflater.inflate(R.layout.browser_game_card_portrait, parent, false);
            } else {
                v = inflater.inflate(R.layout.browser_game_card_landscape, parent, false);
            }
            return new ViewHolder(v);
        }

        // Replace the contents of a view (invoked by the layout manager)
        @Override
        public void onBindViewHolder(final ViewHolder holder, final int position) {
            final Game game = gameList.get(position);

            // Title
            holder.title.setText(game.getTitle());

            // TitleScreen Image
            // TODO : Implement a caching system for not load
            holder.titleScreen.setImageBitmap(game.getTitleScreen());
            holder.titleScreen.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Game selectedGame = gameList.get(position);
                    GameBrowserActivity.selectedGame = selectedGame;

                    if (!selectedGame.read_project_preferences_encoding()) {
                        // TODO : API30 use DocumentFile instead of File class
                        File iniFile = GameBrowserHelper.getIniOfGame(selectedGame.getGameFolderPath(), false);

                        // Retrieve the current region (to check the correct radio button)
                        if (iniFile != null) {
                            iniReader = null;
                            try {
                                iniReader = new IniEncodingReader(iniFile);
                                String encoding = iniReader.getEncoding();
                                selectedGame.setEncoding(encoding);
                            } catch (IOException e) {
                            }
                        }
                    }

                    GameBrowserHelper.launchGame(activity, selectedGame);
                }
            });

            // Settings Button
            holder.settingsButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String[] choices_list = {activity.getResources().getString(R.string.select_game_region), activity.getString(R.string.change_the_layout)};

                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder
                            .setTitle(R.string.settings)
                            .setItems(choices_list, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    switch (which) {
                                        case 0:
                                            chooseRegion(activity, gameList.get(position));
                                            break;
                                        case 1:
                                            chooseLayout(activity, gameList.get(position));
                                            break;
                                        default:
                                            break;
                                    }
                                }
                            });
                    builder.show();
                }
            });

            // FavoriteButton
            updateFavoriteButton(holder, game);
            holder.favoriteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    game.setFavorite(!game.isFavorite());
                    updateFavoriteButton(holder, game);
                    ((GameBrowserActivity)activity).scanAndDisplayGamesList(activity);
                }
            });
        }

        public void updateFavoriteButton(ViewHolder holder, Game game){
            int buttonImageResource;

            if (game.isFavorite()) {
                if (nbOfGamesPerLine == 1) {
                    buttonImageResource = R.drawable.ic_action_favorite_on_black;
                } else {
                    buttonImageResource = R.drawable.ic_action_favorite_on_white;
                }
            } else {
                if (nbOfGamesPerLine == 1) {
                    buttonImageResource = R.drawable.ic_action_favorite_off_black;
                } else {
                    buttonImageResource = R.drawable.ic_action_favorite_off_white;
                }
            }

            holder.favoriteButton.setImageResource(buttonImageResource);
        }

        public void chooseLayout(final Context context, final Game pi) {
            final ButtonMappingManager buttonMappingManager = ButtonMappingManager.getInstance(context);
            String[] layout_name_array = buttonMappingManager.getLayoutsNames();

            //Detect default layout
            pi.getProjectInputLayout(buttonMappingManager);
            int id = -1;
            for (int i = 0; i < buttonMappingManager.getLayoutList().size(); i++) {
                if (buttonMappingManager.getLayoutList().get(i).getId() == pi.getId_input_layout()) {
                    id = i;
                    break;
                }
            }

            final ArrayList<Integer> selected = new ArrayList<Integer>();
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder
                    .setTitle(R.string.choose_layout)
                    .setSingleChoiceItems(layout_name_array, id, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            selected.clear();
                            selected.add(Integer.valueOf(which));
                        }
                    })
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            if (!selected.isEmpty()) {
                                pi.setId_input_layout(buttonMappingManager.getLayoutList().get(selected.get(0)).getId());
                                pi.write_project_preferences();
                            }
                        }
                    })
                    .setNegativeButton(R.string.cancel, null);
            builder.show();
        }

        public void chooseRegion(final Context context, final Game pi) {
            //The list of region choices
            String[] region_array = {
                    context.getString(R.string.autodetect),
                    context.getString(R.string.west_europe),
                    context.getString(R.string.east_europe),
                    context.getString(R.string.japan),
                    context.getString(R.string.cyrillic),
                    context.getString(R.string.korean),
                    context.getString(R.string.chinese_simple),
                    context.getString(R.string.chinese_traditional),
                    context.getString(R.string.greek),
                    context.getString(R.string.turkish),
                    context.getString(R.string.baltic)
            };

            //Retrieve the project's .ini file
            String encoding = null;

            if (!pi.read_project_preferences_encoding()) {
                File iniFile = GameBrowserHelper.getIniOfGame(pi.getGameFolderPath(), false);

                // Retrieve the current region (to check the correct radio button)
                if (iniFile != null) {
                    iniReader = null;
                    try {
                        iniReader = new IniEncodingReader(iniFile);
                        encoding = iniReader.getEncoding();
                    } catch (IOException e) {
                    }
                }
            } else {
                encoding = pi.getEncoding();
            }

            int id = -1;

            if (encoding == null || encoding.equals("auto")) {
                id = 0;
            } else if (encoding.equals("1252")) {
                id = 1;
            } else if (encoding.equals("1250")) {
                id = 2;
            } else if (encoding.equals("932")) {
                id = 3;
            } else if (encoding.equals("1251")) {
                id = 4;
            } else if (encoding.equals("949")) {
                id = 5;
            } else if (encoding.equals("936")) {
                id = 6;
            } else if (encoding.equals("950")) {
                id = 7;
            } else if (encoding.equals("1253")) {
                id = 8;
            } else if (encoding.equals("1254")) {
                id = 9;
            } else if (encoding.equals("1257")) {
                id = 10;
            }

            if (id == -1) {
                Toast.makeText(context, context.getString(R.string.unknown_region), Toast.LENGTH_LONG).show();
            }

            //Building the dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder
                    .setTitle(R.string.select_game_region)
                    .setSingleChoiceItems(region_array, id, null)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            int selectedPosition = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                            String encoding = null;

                            if (selectedPosition == 0) {
                                encoding = "auto";
                            } else if (selectedPosition == 1) {
                                encoding = "1252";
                            } else if (selectedPosition == 2) {
                                encoding = "1250";
                            } else if (selectedPosition == 3) {
                                encoding = "932";
                            } else if (selectedPosition == 4) {
                                encoding = "1251";
                            } else if (selectedPosition == 5) {
                                encoding = "949";
                            } else if (selectedPosition == 6) {
                                encoding = "936";
                            } else if (selectedPosition == 7) {
                                encoding = "950";
                            } else if (selectedPosition == 8) {
                                encoding = "1253";
                            } else if (selectedPosition == 9) {
                                encoding = "1254";
                            } else if (selectedPosition == 10) {
                                encoding = "1257";
                            }

                            if (encoding != null) {
                                pi.setEncoding(encoding);

                                pi.write_project_preferences();
                            }
                        }
                    })
                    .setNegativeButton(R.string.cancel, null);
            builder.show();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            public TextView title;
            public ImageView titleScreen;
            public ImageButton settingsButton, favoriteButton;

            public ViewHolder(View v) {
                super(v);
                this.title = (TextView) v.findViewById(R.id.title);
                this.titleScreen = (ImageView) v.findViewById(R.id.screen);
                this.settingsButton = (ImageButton) v.findViewById(R.id.game_browser_thumbnail_option_button);
                this.favoriteButton = (ImageButton) v.findViewById(R.id.game_browser_thumbnail_favorite_button);
            }
        }
    }

    static class ErrorAdapter extends RecyclerView.Adapter<ErrorAdapter.ErrorViewHolder> {
        List<String> errorList;

        public ErrorAdapter(List<String> errorList, Context context) {
            this.errorList = errorList;
        }

        @Override
        public ErrorViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.browser_error_text, parent, false);
            ErrorViewHolder viewHolder = new ErrorViewHolder(v);
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(ErrorViewHolder holder, int position) {
            holder.text.setText(errorList.get(position).toString());
        }

        @Override
        public int getItemCount() {
            return errorList.size();
        }

        public static class ErrorViewHolder extends RecyclerView.ViewHolder {
            protected TextView text;

            public ErrorViewHolder(View itemView) {
                super(itemView);
                text = (TextView) itemView.findViewById(R.id.error_code);
            }
        }
    }
}
