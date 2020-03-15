for($i = 1; $i -le 7; $i++)
{
    $path = "../Server/src/main/resources/Server$i"
    Write-Host "Clearing directory '$path'..."
    Remove-Item -Path $path -Force
}