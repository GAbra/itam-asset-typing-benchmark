param(
  [Parameter(Mandatory=$true)][string]$SourceCommit,
  [string]$Image = 'itam-asset-typing-benchmark:prebuilt'
)
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$output = 'benchmark-results/baseline-v2'
if (Test-Path "$output/environment.json") { throw 'Baseline already exists; use a separate experiment directory.' }
New-Item -ItemType Directory -Force $output,results/baseline-logs,data/generated | Out-Null
$root = (Get-Location).Path
$imageInfo = docker image inspect $Image | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) { throw 'Runtime image unavailable' }
$cpu = Get-CimInstance Win32_Processor
$system = Get-CimInstance Win32_ComputerSystem
$os = Get-CimInstance Win32_OperatingSystem
$dockerVersion = docker version --format '{{json .}}' | ConvertFrom-Json
$runningContainers = @(docker ps -q).Count
$environment = [ordered]@{
  startedAtUtc = [DateTime]::UtcNow.ToString('o')
  sourceCommit = $SourceCommit
  measurementScriptCommit = (git rev-parse HEAD).Trim()
  cpu = @($cpu | Select-Object Name,NumberOfCores,NumberOfLogicalProcessors)
  physicalRamBytes = $system.TotalPhysicalMemory
  hostOS = ($os | Select-Object Caption,Version,BuildNumber,OSArchitecture)
  docker = $dockerVersion
  dockerHost = (docker info --format '{{json .}}' | ConvertFrom-Json | Select-Object NCPU,MemTotal,KernelVersion,OperatingSystem)
  jarSha256 = ((docker run --rm --entrypoint sha256sum $imageInfo[0].Id /opt/itam/itam-asset-typing-benchmark.jar) -split '\s+')[0]
  wslVersion = ((wsl --version | Out-String) -replace "`0", '').Trim()
  powerScheme = (powercfg /getactivescheme | Out-String).Trim()
  otherRunningContainersAtStart = $runningContainers
  imageId = $imageInfo[0].Id
  containerPlatform = "$($imageInfo[0].Os)/$($imageInfo[0].Architecture)"
  containerCpuQuota = 4
  containerMemoryBytes = 4294967296
  containerMemoryPlusSwapBytes = 4294967296
  jvmFlags = '-Xms2g -Xmx2g -XX:+UseG1GC -XX:ActiveProcessorCount=4'
  seed = 20260909
  warmup = 2
  measuredRuns = 5
  notes = 'Local workstation; background workload not controlled. Fixed engine order; one JVM per measurement invocation.'
}
$environment | ConvertTo-Json -Depth 12 | Set-Content "$output/environment.json" -Encoding utf8
$common = @('run','--rm','--cpus','4','--memory','4g','--memory-swap','4g',
  '-e','JAVA_TOOL_OPTIONS=-Xms2g -Xmx2g -XX:+UseG1GC -XX:ActiveProcessorCount=4',
  '-v',"${root}:/workspace",'-w','/workspace',$imageInfo[0].Id)
$commands = @()
foreach ($count in @(100000,500000,1000000)) {
  $data = "data/generated/baseline-v2-$count.jsonl"
  $batch = if ($count -eq 100000) {5000} else {10000}
  $steps = @(
    @('generate','--count',"$count",'--seed','20260909','--out',$data),
    @('verify','--data',$data,'--out',"$output/verify-$count.json"),
    @('benchmark','--data',$data,'--warmup','2','--runs','5','--batch',"$batch",'--out',"$output/benchmark-$count.json")
  )
  foreach ($step in $steps) {
    Write-Host "$($step[0]): $count assets"
    $commands += 'docker run --rm --cpus 4 --memory 4g --memory-swap 4g -e "JAVA_TOOL_OPTIONS=-Xms2g -Xmx2g -XX:+UseG1GC -XX:ActiveProcessorCount=4" -v "<repository>:/workspace" -w /workspace ' + $imageInfo[0].Id + ' ' + ($step -join ' ')
    & docker @common @step *> "results/baseline-logs/$($step[0])-$count.log"
    if ($LASTEXITCODE -ne 0) { throw "$($step[0]) failed for $count; see results/baseline-logs" }
  }
  Copy-Item "$data.meta.json" "$output/generation-$count.json"
}
$commands | Set-Content "$output/commands.txt" -Encoding utf8
$environment['completedAtUtc'] = [DateTime]::UtcNow.ToString('o')
$environment | ConvertTo-Json -Depth 12 | Set-Content "$output/environment.json" -Encoding utf8
Write-Host 'Baseline complete.'
