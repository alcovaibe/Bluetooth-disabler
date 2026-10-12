using BluetoothDisable.App.ViewModels;
using BluetoothDisable.Core.Services;
using BluetoothDisable.Windows.Discovery;
using Microsoft.UI.Xaml;

namespace BluetoothDisable.App;

public sealed partial class MainWindow : Window
{
    public MainViewModel ViewModel { get; } = new(new BluetoothAdapterService(new WindowsBluetoothAdapterDiscovery()));
    public MainWindow()
    {
        InitializeComponent();
        AppWindow.SetIcon(System.IO.Path.Combine(System.AppContext.BaseDirectory, "Assets", "AppIcon.ico"));
        AppWindow.Resize(new global::Windows.Graphics.SizeInt32(900, 850));
        Closed += (_, _) => ViewModel.Dispose();
    }
    private async void OnLoaded(object sender, RoutedEventArgs e) => await ViewModel.RefreshAsync();
    private async void OnRefresh(object sender, RoutedEventArgs e) => await ViewModel.RefreshAsync();
    public Visibility ToVisibility(bool value) => value ? Visibility.Visible : Visibility.Collapsed;
}
