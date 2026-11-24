# Applies p21_glyphs_sizes.diff, prepared and built locally (assembleDebug, unit tests, lint).
import pathlib,subprocess
diff=pathlib.Path(__file__).with_suffix('.diff')
subprocess.run(['git','apply','--check',str(diff)],check=True)
subprocess.run(['git','apply',str(diff)],check=True)
print('applied',diff.name)
